package com.equipcare;

import com.equipcare.common.result.Result;
import com.equipcare.security.dto.LoginRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第五阶段集成测试：维修工单中心。
 *
 * <p>对应验收清单：
 * <ol>
 *   <li>完整走通「报修 → 派单 → 接单 → 开始维修 → 备件 → 提交验收 → 验收退回 → 再次维修 → 验收通过 → 评价」；</li>
 *   <li>非法状态跳转一律 3001，不允许跨状态；</li>
 *   <li>重复提交：重复开始维修、重复提交验收、重复验收、重复评价全部被拒；</li>
 *   <li>越权操作：只读用户报修/派单、维修人验收、验收人接单、维修人取消均 403；</li>
 *   <li>并发接单：两个线程同时接单，只有一个能成功，另一个收到状态冲突；</li>
 *   <li>工单历史：时间线覆盖六类来源，每条都关联真实操作人且可回查源记录；</li>
 *   <li>不存在可直接改状态的接口：PUT / PATCH 工单均 405。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
class Stage5IT {

    private static final String ADMIN_USERNAME = "stage5_admin";
    private static final String ADMIN_PASSWORD = "Stage5Admin123";
    private static final String REPAIR_USERNAME = "stage5_repair";
    private static final String REPAIR_PASSWORD = "Stage5Repair123";
    private static final String VERIFIER_USERNAME = "stage5_verifier";
    private static final String VERIFIER_PASSWORD = "Stage5Verify123";
    private static final String VIEWER_USERNAME = "stage5_viewer";
    private static final String VIEWER_PASSWORD = "Stage5Viewer123";

    private static final String[] ADMIN_PERMISSIONS = {
            "asset:equipment:view", "asset:equipment:create",
            "wo:workorder:view", "wo:workorder:create", "wo:workorder:dispatch",
            "wo:workorder:accept", "wo:workorder:reject", "wo:workorder:repair",
            "wo:workorder:verify", "wo:workorder:cancel", "wo:workorder:force-transfer",
            "wo:workorder:workbench", "wo:workorder:board", "wo:workorder:evaluate",
            "spare:part:view", "spare:part:manage", "spare:stock:in", "spare:issue", "spare:apply",
            "system:message:view"
    };

    private static final String[] REPAIR_PERMISSIONS = {
            "asset:equipment:view", "wo:workorder:view", "wo:workorder:accept",
            "wo:workorder:reject", "wo:workorder:repair", "wo:workorder:workbench",
            "wo:workorder:evaluate", "spare:part:view", "spare:apply"
    };

    private static final String[] VERIFIER_PERMISSIONS = {
            "asset:equipment:view", "wo:workorder:view", "wo:workorder:verify",
            "wo:workorder:board", "wo:workorder:evaluate", "spare:part:view"
    };

    private static final String[] VIEWER_PERMISSIONS = {
            "asset:equipment:view", "wo:workorder:view"
    };

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String repairToken;
    private String verifierToken;
    private String viewerToken;

    private Long adminUserId;
    private Long repairUserId;
    private Long categoryId;
    private Long locationId;
    private Long equipmentId;
    private Long partId;
    private Long imageFileId;

    /** 主流程工单 */
    private Long orderId;
    /** 并发接单用例的工单 */
    private Long concurrentOrderId;

    @BeforeAll
    void setUp() {
        cleanup();
        // 角色码必须与状态机里的 ROLE_* 常量一致：状态机按角色码判断「谁能执行哪个动作」，
        // 只授权限码而用自定义角色码，会让 allowedActions 变空、动作被 403 拦下。
        Long adminRole = ensureRole("ADMIN", "五阶段管理员", "ALL");
        Long repairRole = ensureRole("REPAIRMAN", "五阶段维修员", "SELF");
        Long verifierRole = ensureRole("VERIFIER", "五阶段验收员", "ALL");
        Long viewerRole = ensureRole("VIEWER", "五阶段只读", "SELF");
        grantAll(adminRole, ADMIN_PERMISSIONS);
        grantAll(repairRole, REPAIR_PERMISSIONS);
        grantAll(verifierRole, VERIFIER_PERMISSIONS);
        grantAll(viewerRole, VIEWER_PERMISSIONS);

        adminUserId = createUser(ADMIN_USERNAME, ADMIN_PASSWORD, "五阶段管理员", adminRole);
        repairUserId = createUser(REPAIR_USERNAME, REPAIR_PASSWORD, "五阶段维修员", repairRole);
        createUser(VERIFIER_USERNAME, VERIFIER_PASSWORD, "五阶段验收员", verifierRole);
        createUser(VIEWER_USERNAME, VIEWER_PASSWORD, "五阶段只读用户", viewerRole);

        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        jdbcTemplate.update("INSERT INTO eq_category (parent_id, category_code, category_name, sort, status) "
                + "VALUES (0, ?, '五阶段测试类别', 92, 1)", "T5C" + suffix);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_category WHERE category_code = ?", Long.class, "T5C" + suffix);
        jdbcTemplate.update("INSERT INTO eq_location (parent_id, location_code, location_name, location_type, "
                + "full_path, sort, status) VALUES (0, ?, '五阶段测试位置', 'ROOM', '测试园区/五阶段位置', 92, 1)",
                "T5LOC" + suffix);
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_location WHERE location_code = ?", Long.class, "T5LOC" + suffix);

        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        repairToken = login(REPAIR_USERNAME, REPAIR_PASSWORD);
        verifierToken = login(VERIFIER_USERNAME, VERIFIER_PASSWORD);
        viewerToken = login(VIEWER_USERNAME, VIEWER_PASSWORD);

        equipmentId = createEquipment("五阶段测试空压机", "T5-" + suffix);
        imageFileId = uploadImage();
    }

    @AfterAll
    void cleanupAll() {
        cleanup();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_message WHERE receiver_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'stage5\\_%')");
        // 工单、状态日志、验收记录按业务规则不可删除（append-only 触发器 + 外键），
        // 这里只清可回收的测试数据，工单留痕保留在测试库。
        jdbcTemplate.update("DELETE FROM wo_evaluation WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE order_no LIKE 'WO%' AND title LIKE '五阶段%')");
        jdbcTemplate.update("DELETE FROM wo_repair_record WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '五阶段%')");
        jdbcTemplate.update("DELETE FROM wo_dispatch_log WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '五阶段%')");
        jdbcTemplate.update("UPDATE eq_equipment SET deleted = 1 WHERE asset_no LIKE 'T5-%'");
        jdbcTemplate.update("UPDATE eq_category SET deleted = 1 WHERE category_code LIKE 'T5C%'");
        jdbcTemplate.update("UPDATE eq_location SET deleted = 1 WHERE location_code LIKE 'T5LOC%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'stage5\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'stage5\\_%'");
        // 角色与授权保留：这里用的是业务标准角色码（ADMIN / REPAIRMAN / VERIFIER / VIEWER），
        // 贸然删除会连带删掉其他用例正在使用的授权。
    }

    /* ------------------------------------------------------------------ */
    /* 用例                                                                */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(1)
    @DisplayName("1. 报修建单：状态待派单、故障照片可回查、派单可设优先级与完成时限")
    void reportAndDispatch() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentId", equipmentId);
        payload.put("title", "五阶段: 空压机异响");
        payload.put("faultType", "MECHANICAL");
        payload.put("faultDesc", "运行中机头位置持续金属异响，压力表波动明显");
        payload.put("level", "P2");
        payload.put("faultImageIds", String.valueOf(imageFileId));

        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/workorder", adminToken, payload);
        assertThat(codeOf(created)).isEqualTo(200);
        orderId = ((Number) created.getBody().getData()).longValue();

        Map<String, Object> detail = detailOf(orderId, adminToken);
        assertThat(detail.get("status")).isEqualTo("PENDING_ASSIGN");
        assertThat(detail.get("statusLabel")).isEqualTo("待派单");
        assertThat(detail.get("priorityLabel")).isEqualTo("中");
        List<Map<String, Object>> faultImages = (List<Map<String, Object>>) detail.get("faultImages");
        assertThat(faultImages).hasSize(1);
        assertThat(faultImages.get(0).get("id")).isEqualTo(imageFileId.intValue());
        assertThat((List<String>) detail.get("allowedActions")).contains("DISPATCH", "CANCEL");

        // 派单：优先级 HIGH + 完成时限 72 小时 + 派单说明
        Map<String, Object> dispatch = new HashMap<>();
        dispatch.put("assigneeId", repairUserId);
        dispatch.put("priority", "HIGH");
        dispatch.put("resolveHours", 72);
        dispatch.put("reason", "现场已停机，请优先处理机头轴承");
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/dispatch", adminToken, dispatch)))
                .isEqualTo(200);

        Map<String, Object> after = detailOf(orderId, adminToken);
        assertThat(after.get("status")).isEqualTo("PENDING_ACCEPT");
        assertThat(after.get("priority")).isEqualTo("HIGH");
        assertThat(after.get("priorityLabel")).isEqualTo("高");
        assertThat(after.get("assigneeId")).isEqualTo(repairUserId.intValue());
        assertThat(((List<Map<String, Object>>) after.get("dispatchRecords"))).hasSize(1);
        assertThat(after.get("resolveDueTime")).isNotNull();

        // 维修人视角的可执行动作：接单 / 拒接 / 转派（转派需派单权限，维修人没有）
        Map<String, Object> repairView = detailOf(orderId, repairToken);
        assertThat((List<String>) repairView.get("allowedActions"))
                .contains("ACCEPT", "REJECT_DISPATCH")
                .doesNotContain("DISPATCH");
    }

    @Test
    @Order(2)
    @DisplayName("2. 非法状态跳转：未接单不能提交验收、未提交验收不能验收、待接单不能再派单")
    void illegalTransitions() {
        Map<String, Object> submit = Map.of("repairSummary", "想直接提交验收", "laborHours", 1);
        ResponseEntity<Result> r1 = call(HttpMethod.POST, "/api/workorder/" + orderId + "/submit-verify",
                repairToken, submit);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(r1)).isEqualTo(3001);
        assertThat(r1.getBody().getMessage()).contains("待接单");

        Map<String, Object> verify = Map.of("qualityScore", 5, "remark", "跳过维修直接验收");
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/verify-pass",
                verifierToken, verify))).isEqualTo(3001);

        Map<String, Object> dispatchAgain = Map.of("assigneeId", repairUserId);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/dispatch",
                adminToken, dispatchAgain))).isEqualTo(3001);

        // 状态没有被任何一次失败调用改动
        assertThat(detailOf(orderId, adminToken).get("status")).isEqualTo("PENDING_ACCEPT");
    }

    @Test
    @Order(3)
    @DisplayName("3. 接单与开始维修：未开始维修不能提交验收，重复开始维修被拒")
    void acceptAndStartRepair() {
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/accept", repairToken, null)))
                .isEqualTo(200);
        Map<String, Object> afterAccept = detailOf(orderId, repairToken);
        assertThat(afterAccept.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(afterAccept.get("acceptTime")).isNotNull();
        assertThat(afterAccept.get("repairStarted")).isEqualTo(false);

        // 未开始维修 → 提交验收被拦（工单没有维修时长，验收无从判断工期）
        // 业务前置条件不满足统一返回 2004「当前状态不允许该操作」，与权限不足的 403 区分
        Map<String, Object> submit = Map.of("repairSummary", "想跳过开始维修", "laborHours", 1);
        ResponseEntity<Result> blocked = call(HttpMethod.POST, "/api/workorder/" + orderId + "/submit-verify",
                repairToken, submit);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(blocked)).isEqualTo(2004);
        assertThat(blocked.getBody().getMessage()).contains("开始维修");

        Map<String, Object> start = new HashMap<>();
        start.put("reason", "机头轴承磨损导致转子偏心");
        start.put("diagnosis", "听针定位机头轴承位，振动值 8.6mm/s，超报警值");
        start.put("beforeImageIds", String.valueOf(imageFileId));
        start.put("estimateMinutes", 180);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/start-repair",
                repairToken, start))).isEqualTo(200);

        // 重复开始维修：条件更新 + 显式状态校验双重拦截
        ResponseEntity<Result> dup = call(HttpMethod.POST, "/api/workorder/" + orderId + "/start-repair",
                repairToken, start);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody().getMessage()).contains("已开始维修");

        Map<String, Object> afterStart = detailOf(orderId, repairToken);
        assertThat(afterStart.get("repairStarted")).isEqualTo(true);
        List<Map<String, Object>> records = (List<Map<String, Object>>) afterStart.get("repairRecords");
        assertThat(records).hasSize(2);
        assertThat(records.get(0).get("recordType")).isEqualTo("START");
        assertThat(records.get(0).get("content").toString()).contains("机头轴承磨损");
        assertThat(((List<?>) records.get(0).get("beforeImages"))).hasSize(1);
        assertThat(records.get(1).get("recordType")).isEqualTo("DIAGNOSIS");
    }

    @Test
    @Order(4)
    @DisplayName("4. 备件申请未审批时禁止提交验收，完工照片缺失同样被拒")
    void partApplicationBlocksVerify() {
        // 备件档案 + 入库 + 申请
        Map<String, Object> part = new HashMap<>();
        part.put("partNo", "T5PART" + System.currentTimeMillis() % 100000);
        part.put("partName", "五阶段测试轴承");
        part.put("unit", "个");
        part.put("spec", "6208-2RS");
        part.put("unitPrice", 128.50);
        part.put("safetyStock", 1);
        ResponseEntity<Result> partCreated = call(HttpMethod.POST, "/api/inventory/parts", adminToken, part);
        assertThat(codeOf(partCreated)).isEqualTo(200);
        partId = ((Number) partCreated.getBody().getData()).longValue();

        Map<String, Object> stockIn = new HashMap<>();
        stockIn.put("partId", partId);
        stockIn.put("quantity", 5);
        stockIn.put("remark", "五阶段测试入库");
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/stock/in", adminToken, stockIn)))
                .isEqualTo(200);

        Map<String, Object> apply = new HashMap<>();
        apply.put("workOrderId", orderId);
        apply.put("partId", partId);
        apply.put("quantity", 1);
        apply.put("remark", "更换机头轴承");
        ResponseEntity<Result> applied = call(HttpMethod.POST, "/api/inventory/applies", repairToken, apply);
        assertThat(codeOf(applied)).isEqualTo(200);
        Long applyId = ((Number) applied.getBody().getData()).longValue();

        // 有 FINISH 记录与维修后照片，但备件还在审批 → 仍不能提交
        Map<String, Object> finish = new HashMap<>();
        finish.put("recordType", "FINISH");
        finish.put("content", "更换机头轴承并做动平衡，空载试运行 30 分钟无异响");
        finish.put("afterImageIds", String.valueOf(imageFileId));
        finish.put("costMinutes", 95);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/records",
                repairToken, finish))).isEqualTo(200);

        Map<String, Object> submit = Map.of("repairSummary", "更换轴承并试运行合格", "laborHours", 1.5);
        ResponseEntity<Result> blocked = call(HttpMethod.POST, "/api/workorder/" + orderId + "/submit-verify",
                repairToken, submit);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getBody().getMessage()).contains("备件申请");

        // 出库后再提交 → 通过
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/issue",
                adminToken, null))).isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/submit-verify",
                repairToken, submit))).isEqualTo(200);
        assertThat(detailOf(orderId, adminToken).get("status")).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @Order(5)
    @DisplayName("5. 验收不通过退回维修中：原因不足 10 字被拒，退回后驳回次数累加且可再次提交")
    void verifyRejectReturnsToRepair() {
        Map<String, Object> shortRemark = Map.of("remark", "不行");
        ResponseEntity<Result> tooShort = call(HttpMethod.POST, "/api/workorder/" + orderId + "/verify-reject",
                verifierToken, shortRemark);
        assertThat(tooShort.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooShort.getBody().getMessage()).contains("不少于 10 个字");

        Map<String, Object> reject = Map.of("remark", "试运行仍有轻微异响，需复查联轴器同心度");
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/verify-reject",
                verifierToken, reject))).isEqualTo(200);

        Map<String, Object> after = detailOf(orderId, adminToken);
        assertThat(after.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(after.get("rejectCount")).isEqualTo(1);
        assertThat(after.get("submitVerifyTime")).isNull();
        assertThat(((List<Map<String, Object>>) after.get("verifyRecords"))).hasSize(1);
        assertThat(((List<Map<String, Object>>) after.get("verifyRecords")).get(0).get("verifyResult"))
                .isEqualTo("REJECT");

        // 再次维修并重新提交
        Map<String, Object> record = new HashMap<>();
        record.put("recordType", "PROCESS");
        record.put("content", "重新调整联轴器同心度，径向偏差由 0.18mm 校正到 0.05mm");
        record.put("costMinutes", 40);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/records",
                repairToken, record))).isEqualTo(200);
        Map<String, Object> resubmit = Map.of("repairSummary", "二次校正后复测合格", "laborHours", 2.2);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/submit-verify",
                repairToken, resubmit))).isEqualTo(200);
        assertThat(detailOf(orderId, adminToken).get("status")).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @Order(6)
    @DisplayName("6. 并发接单：两个线程同时接单，只有一个成功，另一个收到状态冲突")
    void concurrentAccept() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentId", equipmentId);
        payload.put("title", "五阶段: 并发接单用例");
        payload.put("faultType", "ELECTRIC");
        payload.put("faultDesc", "控制柜接触器频繁跳闸");
        payload.put("level", "P3");
        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/workorder", adminToken, payload);
        assertThat(codeOf(created)).isEqualTo(200);
        concurrentOrderId = ((Number) created.getBody().getData()).longValue();

        Map<String, Object> dispatch = Map.of("assigneeId", repairUserId, "priority", "NORMAL");
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + concurrentOrderId + "/dispatch",
                adminToken, dispatch))).isEqualTo(200);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        Callable<ResponseEntity<Result>> task = () -> {
            ready.countDown();
            go.await(5, TimeUnit.SECONDS);
            return call(HttpMethod.POST, "/api/workorder/" + concurrentOrderId + "/accept", repairToken, null);
        };
        Future<ResponseEntity<Result>> f1 = pool.submit(task);
        Future<ResponseEntity<Result>> f2 = pool.submit(task);
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        int ok = 0;
        List<Integer> failedCodes = new ArrayList<>();
        for (Future<ResponseEntity<Result>> f : List.of(f1, f2)) {
            ResponseEntity<Result> resp = f.get(20, TimeUnit.SECONDS);
            if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                ok++;
            } else {
                failedCodes.add(resp.getBody() == null ? -1 : resp.getBody().getCode());
            }
        }
        pool.shutdownNow();

        assertThat(ok).as("同一张工单只能被接一次").isEqualTo(1);
        assertThat(failedCodes).hasSize(1);
        // 3002=版本冲突（同时读到同一 version），3001=后到者已看到状态变化
        assertThat(failedCodes.get(0)).isIn(3001, 3002);
        assertThat(detailOf(concurrentOrderId, adminToken).get("status")).isEqualTo("IN_PROGRESS");
    }

    @Test
    @Order(7)
    @DisplayName("7. 验收通过与服务评价：重复评价被拒，评分可回查")
    void verifyPassAndEvaluate() {
        Map<String, Object> verify = Map.of("qualityScore", 5, "remark", "复测振动值 2.1mm/s，验收合格",
                "attachmentIds", String.valueOf(imageFileId));
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/verify-pass",
                verifierToken, verify))).isEqualTo(200);

        Map<String, Object> after = detailOf(orderId, adminToken);
        assertThat(after.get("status")).isEqualTo("COMPLETED");
        assertThat(after.get("actualFinishTime")).isNotNull();
        assertThat(after.get("closeTime")).isNotNull();
        assertThat(after.get("verifyResult")).isEqualTo("PASS");
        assertThat(after.get("qualityScore")).isEqualTo(5);
        // 备件成本在验收通过时归集
        assertThat(Double.parseDouble(after.get("spareCost").toString())).isGreaterThan(0);

        // 重复验收：已完成工单不再接受验收动作
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/verify-pass",
                verifierToken, verify))).isEqualTo(3001);

        Map<String, Object> evaluate = new HashMap<>();
        evaluate.put("attitudeScore", 5);
        evaluate.put("qualityScore", 4);
        evaluate.put("speedScore", 4);
        evaluate.put("content", "响应及时，返工后处理到位，现场清理干净");
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/evaluate",
                adminToken, evaluate))).isEqualTo(200);

        Map<String, Object> evaluated = detailOf(orderId, adminToken);
        Map<String, Object> evaluation = (Map<String, Object>) evaluated.get("evaluation");
        assertThat(evaluation).isNotNull();
        assertThat(evaluation.get("attitudeScore")).isEqualTo(5);
        assertThat(evaluation.get("qualityScore")).isEqualTo(4);
        assertThat(((Number) evaluation.get("averageScore")).doubleValue()).isEqualTo(4.3);
        assertThat(evaluated.get("evaluated")).isEqualTo(true);

        // 重复评价：先查后插 + 唯一索引，两道闸
        ResponseEntity<Result> dup = call(HttpMethod.POST, "/api/workorder/" + orderId + "/evaluate",
                adminToken, evaluate);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody().getMessage()).contains("已评价");
    }

    @Test
    @Order(8)
    @DisplayName("8. 越权操作：四个角色按权限码硬拦截")
    void permissionMatrix() {
        // 只读用户不能报修
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentId", equipmentId);
        payload.put("title", "五阶段: 越权报修");
        payload.put("faultType", "OTHER");
        payload.put("faultDesc", "只读用户尝试报修");
        payload.put("level", "P3");
        assertThat(call(HttpMethod.POST, "/api/workorder", viewerToken, payload).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 只读用户不能派单
        Map<String, Object> dispatch = Map.of("assigneeId", repairUserId);
        assertThat(call(HttpMethod.POST, "/api/workorder/" + concurrentOrderId + "/dispatch",
                viewerToken, dispatch).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 维修人不能验收
        Map<String, Object> verify = Map.of("qualityScore", 5, "remark", "维修人自己验收自己的工单");
        assertThat(call(HttpMethod.POST, "/api/workorder/" + concurrentOrderId + "/verify-pass",
                repairToken, verify).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 验收人不能接单（无 accept 权限）
        assertThat(call(HttpMethod.POST, "/api/workorder/" + concurrentOrderId + "/accept",
                verifierToken, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 维修人不能取消工单（取消是管理动作，无权限码）
        Map<String, Object> cancel = Map.of("reason", "维修人想自己取消");
        assertThat(call(HttpMethod.POST, "/api/workorder/" + concurrentOrderId + "/cancel",
                repairToken, cancel).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 只读用户能看列表与详情
        assertThat(call(HttpMethod.GET, "/api/workorder/" + orderId, viewerToken, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(9)
    @DisplayName("9. 工单历史：六类来源归并成一条时间线，每条都关联真实操作人并可回查源记录")
    void timelineIntegrity() {
        ResponseEntity<Result> resp = call(HttpMethod.GET, "/api/workorder/" + orderId + "/timeline",
                adminToken, null);
        assertThat(codeOf(resp)).isEqualTo(200);
        List<Map<String, Object>> items = (List<Map<String, Object>>) resp.getBody().getData();
        assertThat(items).isNotEmpty();

        // 第一条必须是创建，最后一条是评价
        assertThat(items.get(0).get("eventType")).isEqualTo("CREATE");
        assertThat(items.get(0).get("title")).isEqualTo("创建工单");
        assertThat(items.get(items.size() - 1).get("eventType")).isEqualTo("EVALUATION");

        // 六类来源齐备
        List<String> types = items.stream().map(i -> (String) i.get("eventType")).distinct().toList();
        assertThat(types).contains("CREATE", "STATUS", "DISPATCH", "REPAIR", "VERIFY", "PART", "EVALUATION");

        // 每条都有关联用户、时间、源记录
        for (Map<String, Object> item : items) {
            assertThat(item.get("operatorId")).as("事件 %s 的操作人", item.get("title")).isNotNull();
            assertThat(item.get("operatorName")).as("事件 %s 的操作人姓名", item.get("title")).isNotNull();
            assertThat(item.get("eventTime")).as("事件 %s 的发生时间", item.get("title")).isNotNull();
            assertThat(item.get("sourceType")).as("事件 %s 的源表", item.get("title")).isNotNull();
            assertThat(item.get("sourceId")).as("事件 %s 的源记录", item.get("title")).isNotNull();
        }

        // 关键节点顺序：接单在开始维修之前，提交验收在验收通过之前
        int acceptIdx = indexOf(items, "STATUS", "接单");
        int startIdx = indexOf(items, "REPAIR", "开始维修");
        int submitIdx = indexOf(items, "STATUS", "提交验收");
        int passIdx = indexOf(items, "VERIFY", "验收通过");
        assertThat(acceptIdx).isGreaterThanOrEqualTo(0);
        assertThat(startIdx).isGreaterThan(acceptIdx);
        assertThat(submitIdx).isGreaterThan(startIdx);
        assertThat(passIdx).isGreaterThan(submitIdx);

        // 备件用料可核对
        ResponseEntity<Result> parts = call(HttpMethod.GET, "/api/workorder/" + orderId + "/parts",
                adminToken, null);
        assertThat(codeOf(parts)).isEqualTo(200);
        List<Map<String, Object>> partList = (List<Map<String, Object>>) parts.getBody().getData();
        assertThat(partList).hasSize(1);
        assertThat(partList.get(0).get("issueStatus")).isEqualTo("ISSUED");
        assertThat(partList.get(0).get("partName")).isEqualTo("五阶段测试轴承");
    }

    @Test
    @Order(10)
    @DisplayName("10. 不存在直接改状态的接口：PUT / PATCH 工单均 405，状态只能由动作推进")
    void noDirectStatusUpdate() {
        Map<String, Object> body = Map.of("status", "COMPLETED");
        assertThat(call(HttpMethod.PUT, "/api/workorder/" + orderId, adminToken, body).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(call(HttpMethod.PATCH, "/api/workorder/" + orderId, adminToken, body).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(detailOf(orderId, adminToken).get("status")).isEqualTo("COMPLETED");
    }

    /* ------------------------------------------------------------------ */
    /* 工具                                                                */
    /* ------------------------------------------------------------------ */

    private int indexOf(List<Map<String, Object>> items, String eventType, String title) {
        for (int i = 0; i < items.size(); i++) {
            if (eventType.equals(items.get(i).get("eventType")) && title.equals(items.get(i).get("title"))) {
                return i;
            }
        }
        return -1;
    }

    private Long ensureRole(String code, String name, String scope) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_role WHERE role_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        if (id != null) {
            return id.longValue();
        }
        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, data_scope, builtin, sort, status) "
                + "VALUES (?, ?, ?, 0, 65, 1)", code, name, scope);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, code);
    }

    private void grantAll(Long roleId, String[] codes) {
        for (String code : codes) {
            Integer id = jdbcTemplate.query("SELECT id FROM sys_permission WHERE perm_code = ?",
                    rs -> rs.next() ? rs.getInt("id") : null, code);
            Long permId;
            if (id == null) {
                jdbcTemplate.update("INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, "
                        + "sort, visible, status) VALUES (0, ?, ?, 'API', 96, 1, 1)", code, code);
                permId = jdbcTemplate.queryForObject(
                        "SELECT id FROM sys_permission WHERE perm_code = ?", Long.class, code);
            } else {
                permId = id.longValue();
            }
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                    roleId, permId);
        }
    }

    private Long createUser(String username, String password, String realName, Long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user (username, password, real_name, status) VALUES (?, ?, ?, 1)",
                username, passwordEncoder.encode(password), realName);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?",
                Long.class, username);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private String login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        ResponseEntity<Result> response = restTemplate.postForEntity("/api/auth/login", request, Result.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        return (String) data.get("accessToken");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private ResponseEntity<Result> call(HttpMethod method, String path, String token, Object body) {
        return restTemplate.exchange(path, method, new HttpEntity<>(body, authHeaders(token)), Result.class);
    }

    private int codeOf(ResponseEntity<Result> response) {
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getCode();
    }

    private Map<String, Object> detailOf(Long id, String token) {
        ResponseEntity<Result> resp = call(HttpMethod.GET, "/api/workorder/" + id, token, null);
        assertThat(codeOf(resp)).isEqualTo(200);
        return (Map<String, Object>) resp.getBody().getData();
    }

    private Long createEquipment(String name, String assetNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentName", name);
        payload.put("categoryId", categoryId);
        payload.put("locationId", locationId);
        payload.put("brand", "测试品牌");
        payload.put("model", "T5-200");
        payload.put("assetNo", assetNo);
        ResponseEntity<Result> response = call(HttpMethod.POST, "/api/asset/equipment", adminToken, payload);
        assertThat(codeOf(response)).isEqualTo(200);
        return ((Number) response.getBody().getData()).longValue();
    }

    /** 上传一张 8×8 的 PNG，用于故障照片与维修前后照片 */
    private Long uploadImage() {
        try {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            ByteArrayResource resource = new ByteArrayResource(out.toByteArray()) {
                @Override
                public String getFilename() {
                    return "stage5.png";
                }
            };
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", resource);
            form.add("bizType", "WORK_ORDER");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(adminToken);
            ResponseEntity<Result> resp = restTemplate.exchange("/api/files/upload", HttpMethod.POST,
                    new HttpEntity<>(form, headers), Result.class);
            assertThat(codeOf(resp)).isEqualTo(200);
            Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
            return ((Number) data.get("fileId")).longValue();
        } catch (Exception e) {
            throw new IllegalStateException("上传测试图片失败", e);
        }
    }
}
