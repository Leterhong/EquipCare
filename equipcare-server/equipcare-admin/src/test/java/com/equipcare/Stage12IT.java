package com.equipcare;

import com.equipcare.common.result.Result;
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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 阶段十二集成测试：现有功能稳定性 / 完整性 / 数据一致性整改回归。
 *
 * <p>覆盖整改专项要求的五条端到端业务流程 + 本轮缺陷修复回归：
 * <ol>
 *   <li>流程一：管理员登录 → 创建设备 → 查看设备详情；</li>
 *   <li>流程二：巡检模板 → 计划 → 生成任务 → 执行 → 提交结果；</li>
 *   <li>流程三：报修 → 派单 → 接单 → 维修 → 验收 → 评价关闭；</li>
 *   <li>流程四：备件 → 入库 → 工单领料 → 退料 → 库存与流水一致；</li>
 *   <li>流程五：受限用户登录 → 允许接口可用 → 越权接口被拒；</li>
 *   <li>Q-01 回归：文件下载按上传人 / 管理员 / 业务域权限放行，上传 bizType 白名单；</li>
 *   <li>Q-02 回归：盘点调整数量必须为正整数，负数 / 零拒绝。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
class Stage12IT {

    private static final String SUFFIX = String.valueOf(System.currentTimeMillis()).substring(7);
    private static final String ADMIN_USER = "stage12_admin";
    private static final String REPAIR_USER = "stage12_repair";
    private static final String VIEWER_USER = "stage12_viewer";
    private static final String PASS = "S12#" + SUFFIX + "x!";

    private static final String[] ADMIN_PERMISSIONS = {
            "asset:equipment:view", "asset:equipment:create", "asset:equipment:update",
            "wo:workorder:view", "wo:workorder:create", "wo:workorder:dispatch",
            "wo:workorder:accept", "wo:workorder:repair", "wo:workorder:verify", "wo:workorder:evaluate",
            "inspect:template:view", "inspect:template:manage", "inspect:plan:view", "inspect:plan:manage",
            "inspect:plan:generate", "inspect:task:view", "inspect:task:execute",
            "spare:part:view", "spare:part:manage", "spare:stock:in", "spare:stock:adjust",
            "spare:issue", "spare:apply", "spare:txn:view", "system:message:view"
    };

    private static final String[] REPAIR_PERMISSIONS = {
            "asset:equipment:view", "wo:workorder:view", "wo:workorder:accept",
            "wo:workorder:repair", "wo:workorder:evaluate",
            "inspect:template:view", "inspect:plan:view", "inspect:task:view", "inspect:task:execute",
            "spare:part:view", "spare:apply"
    };

    /** 受限角色：仅设备查看——用于越权与文件下载权限验证 */
    private static final String[] VIEWER_PERMISSIONS = {
            "asset:equipment:view"
    };

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String repairToken;
    private String viewerToken;
    private Long adminUserId;
    private Long repairUserId;
    private Long categoryId;
    private Long locationId;
    private Long equipmentId;
    private Long partId;

    @BeforeAll
    void setUp() {
        cleanup();
        Long adminRole = ensureRole("ADMIN", "十二阶段管理员");
        Long repairRole = ensureRole("REPAIRMAN", "十二阶段维修员");
        // 注意：不能用 "VIEWER" 作角色码——Stage5IT 在测试库遗留了同名角色且带 wo:workorder:view，
        // ensureRole 复用旧角色会导致越权断言失效（阶段十二实测踩坑）
        Long viewerRole = ensureRole("ST12_VIEWER", "十二阶段查看者");
        for (String code : ADMIN_PERMISSIONS) {
            grantPermission(adminRole, code);
        }
        for (String code : REPAIR_PERMISSIONS) {
            grantPermission(repairRole, code);
        }
        for (String code : VIEWER_PERMISSIONS) {
            grantPermission(viewerRole, code);
        }

        adminUserId = createUser(ADMIN_USER, PASS, "十二阶段管理员", adminRole);
        repairUserId = createUser(REPAIR_USER, PASS, "十二阶段维修员", repairRole);
        createUser(VIEWER_USER, PASS, "十二阶段查看者", viewerRole);

        jdbcTemplate.update("INSERT INTO eq_category (parent_id, category_code, category_name, sort, status) "
                + "VALUES (0, ?, '十二阶段类别', 94, 1)", "T12C" + SUFFIX);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_category WHERE category_code = ?", Long.class, "T12C" + SUFFIX);
        jdbcTemplate.update("INSERT INTO eq_location (parent_id, location_code, location_name, location_type, "
                + "full_path, sort, status) VALUES (0, ?, '十二阶段位置', 'ROOM', '测试园区/十二阶段', 94, 1)",
                "T12LOC" + SUFFIX);
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_location WHERE location_code = ?", Long.class, "T12LOC" + SUFFIX);

        adminToken = loginToken(ADMIN_USER, PASS);
        repairToken = loginToken(REPAIR_USER, PASS);
        viewerToken = loginToken(VIEWER_USER, PASS);
        equipmentId = createEquipment("十二阶段测试风机", "T12-" + SUFFIX);
        partId = createPart("T12-BRG-" + SUFFIX, "十二阶段轴承", new BigDecimal("100.00"), 20);
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sp_work_order_part WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十二阶段%')");
        jdbcTemplate.update("DELETE FROM wo_dispatch_log WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十二阶段%')");
        jdbcTemplate.update("DELETE FROM wo_repair_record WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十二阶段%')");
        jdbcTemplate.update("DELETE FROM wo_verify_record WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十二阶段%')");
        jdbcTemplate.update("DELETE FROM wo_evaluation WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十二阶段%')");
        jdbcTemplate.update("DELETE FROM sla_alert_record WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十二阶段%')");
        jdbcTemplate.update("DELETE FROM insp_task_item WHERE task_id IN (SELECT id FROM insp_task "
                + "WHERE plan_id IN (SELECT id FROM insp_plan WHERE plan_code LIKE 'T12%'))");
        jdbcTemplate.update("DELETE FROM insp_anomaly WHERE task_id IN (SELECT id FROM insp_task "
                + "WHERE plan_id IN (SELECT id FROM insp_plan WHERE plan_code LIKE 'T12%'))");
        jdbcTemplate.update("DELETE FROM insp_task WHERE plan_id IN "
                + "(SELECT id FROM insp_plan WHERE plan_code LIKE 'T12%')");
        jdbcTemplate.update("DELETE FROM insp_plan_equipment WHERE plan_id IN "
                + "(SELECT id FROM insp_plan WHERE plan_code LIKE 'T12%')");
        jdbcTemplate.update("DELETE FROM insp_plan WHERE plan_code LIKE 'T12%'");
        jdbcTemplate.update("DELETE FROM insp_template_item WHERE template_id IN "
                + "(SELECT id FROM insp_template WHERE template_code LIKE 'T12%')");
        jdbcTemplate.update("DELETE FROM insp_template WHERE template_code LIKE 'T12%'");
        jdbcTemplate.update("UPDATE sp_part SET deleted = 1 WHERE part_no LIKE 'T12-%'");
        jdbcTemplate.update("UPDATE eq_equipment SET deleted = 1 WHERE asset_no LIKE 'T12-%'");
        jdbcTemplate.update("UPDATE eq_category SET deleted = 1 WHERE category_code LIKE 'T12C%'");
        jdbcTemplate.update("UPDATE eq_location SET deleted = 1 WHERE location_code LIKE 'T12LOC%'");
        jdbcTemplate.update("DELETE FROM sys_file WHERE file_name IN ('stage12.png', 'stage12.pdf')");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'stage12\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'stage12\\_%'");
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code = 'ST12_VIEWER')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code = 'ST12_VIEWER'");
    }

    /* ------------------------------------------------------------------ */
    /* 流程一：设备                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(1)
    @DisplayName("流程一：创建设备 → 详情与台账一致")
    void flow1Equipment() {
        Map<String, Object> detail = dataOf(call(HttpMethod.GET,
                "/api/asset/equipment/" + equipmentId, adminToken, null));
        assertThat(detail.get("equipmentName")).isEqualTo("十二阶段测试风机");
        assertThat(detail.get("assetNo")).isEqualTo("T12-" + SUFFIX);
        assertThat(((Number) detail.get("id")).longValue()).isEqualTo(equipmentId);

        // 台账分页能查到该设备
        Map<String, Object> page = dataOf(call(HttpMethod.GET,
                "/api/asset/equipment?pageNum=1&pageSize=10&keyword=" + ("T12-" + SUFFIX),
                adminToken, null));
        List<Map<String, Object>> records = (List<Map<String, Object>>) page.get("records");
        assertThat(records).extracting(r -> ((Number) r.get("id")).longValue()).contains(equipmentId);
    }

    /* ------------------------------------------------------------------ */
    /* 流程二：巡检                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(2)
    @DisplayName("流程二：模板 → 发布 → 计划 → 生成任务 → 执行提交")
    void flow2Inspection() {
        // 模板（3 项：枚举 / 数值 / 是否）
        Map<String, Object> payload = new HashMap<>();
        payload.put("templateName", "十二阶段风机巡检模板");
        payload.put("templateCode", "T12TPL" + SUFFIX);
        payload.put("categoryId", categoryId);
        payload.put("description", "阶段十二端到端");
        payload.put("items", List.of(
                item("检查运行声音", "ENUM", Map.of(
                        "enumOptions", "[\"正常\",\"异响\"]", "normalValue", "正常")),
                item("检查外壳温度", "NUMBER", Map.of("unit", "℃", "minValue", 0, "maxValue", 60)),
                item("检查是否漏水", "BOOLEAN", Map.of("normalValue", "0"))));
        Long templateId = createdId(call(HttpMethod.POST, "/api/inspection/templates", adminToken, payload));
        assertThat(codeOf(call(HttpMethod.POST, "/api/inspection/templates/" + templateId + "/publish",
                adminToken, null))).isEqualTo(200);

        // 计划（按分类圈定本设备，每日 08:00，启用）
        Map<String, Object> plan = new HashMap<>();
        plan.put("planName", "十二阶段每日巡检");
        plan.put("planCode", "T12PLAN" + SUFFIX);
        plan.put("templateId", templateId);
        plan.put("scopeType", "CATEGORY");
        plan.put("scopeIds", List.of(categoryId));
        plan.put("executorStrategy", "FIXED");
        plan.put("executorIds", List.of(repairUserId));
        plan.put("cycleType", "DAILY");
        plan.put("executeTime", "08:00");
        plan.put("startDate", LocalDate.now().toString());
        plan.put("endDate", LocalDate.now().plusDays(7).toString());
        plan.put("slaMinutes", 480);
        plan.put("generateAheadHours", 72);
        plan.put("status", "ENABLED");
        Long planId = createdId(call(HttpMethod.POST, "/api/inspection/plans", adminToken, plan));

        // 立即生成：作用域内的设备应产出任务
        Integer taskCount = ((Number) dataOf(call(HttpMethod.POST,
                "/api/inspection/plans/" + planId + "/generate", adminToken, null))).intValue();
        assertThat(taskCount).isGreaterThanOrEqualTo(1);

        // 找到本设备的待执行任务并执行
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM insp_task WHERE plan_id = ? AND equipment_id = ? AND status = 'PENDING' "
                        + "ORDER BY id LIMIT 1", Long.class, planId, equipmentId);
        assertThat(codeOf(call(HttpMethod.POST, "/api/inspection/tasks/" + taskId + "/start",
                repairToken, Map.of()))).isEqualTo(200);

        Map<String, Object> taskDetail = dataOf(call(HttpMethod.GET,
                "/api/inspection/tasks/" + taskId, repairToken, null));
        List<Map<String, Object>> items = (List<Map<String, Object>>) taskDetail.get("items");
        assertThat(items).hasSize(3);

        // 全部正常提交
        Map<String, Object> submit = Map.of("items", List.of(
                Map.of("templateItemId", ((Number) items.get(0).get("templateItemId")).longValue(),
                        "result", "NORMAL", "itemValue", "正常"),
                Map.of("templateItemId", ((Number) items.get(1).get("templateItemId")).longValue(),
                        "result", "NORMAL", "itemValue", "30"),
                Map.of("templateItemId", ((Number) items.get(2).get("templateItemId")).longValue(),
                        "result", "NORMAL", "itemValue", "0")));
        assertThat(codeOf(call(HttpMethod.POST, "/api/inspection/tasks/" + taskId + "/submit",
                repairToken, submit))).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM insp_task WHERE id = ?", String.class, taskId)).isEqualTo("SUBMITTED");

        // 已提交任务不能重复提交（非法重复提交拦截）
        assertThat(codeOf(call(HttpMethod.POST, "/api/inspection/tasks/" + taskId + "/submit",
                repairToken, submit))).isEqualTo(2004);
    }

    /* ------------------------------------------------------------------ */
    /* 流程三：工单                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(3)
    @DisplayName("流程三：报修 → 派单 → 接单 → 维修 → 验收 → 评价 → 关闭")
    void flow3WorkOrder() throws Exception {
        // 报修
        Map<String, Object> create = new HashMap<>();
        create.put("equipmentId", equipmentId);
        create.put("title", "十二阶段: 全流程工单");
        create.put("faultType", "MECHANICAL");
        create.put("faultDesc", "阶段十二端到端测试报修");
        create.put("level", "P3");
        Long orderId = createdId(call(HttpMethod.POST, "/api/workorder", adminToken, create));

        // 派单 → 接单
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/dispatch", adminToken,
                Map.of("assigneeId", repairUserId, "reason", "阶段十二测试派单")))).isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/accept",
                repairToken, null))).isEqualTo(200);

        // 维修：开始 → FINISH 记录 → 提交验收
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/start-repair",
                repairToken, Map.of("reason", "轴承磨损导致振动超标，需更换",
                        "diagnosis", "听针定位轴承位，振动值 8.6mm/s 超报警值")))).isEqualTo(200);
        Map<String, Object> record = new HashMap<>();
        record.put("recordType", "FINISH");
        record.put("content", "更换轴承并空载试运行 30 分钟无异响");
        record.put("costMinutes", 90);
        // FINISH 记录必须带维修后照片
        Long finishImageId = uploadFile(repairToken, "stage12-finish.png", "WORK_ORDER");
        record.put("afterImageIds", String.valueOf(finishImageId));
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/records",
                repairToken, record))).isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/submit-verify",
                repairToken, Map.of("repairSummary", "更换轴承试运行合格", "laborHours", 1.5))))
                .isEqualTo(200);
        assertThat(detailStatus(orderId)).isEqualTo("PENDING_REVIEW");

        // 验收通过 → 工单完结 → 报修人评价（评价为完结后动作，不改变状态）
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/verify-pass",
                adminToken, Map.of("qualityScore", 5, "remark", "维修质量合格，试运行无异常")))).isEqualTo(200);
        assertThat(detailStatus(orderId)).isEqualTo("COMPLETED");
        // 评价人=报修人本人（维修员评价被拒）
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/evaluate",
                repairToken, Map.of("attitudeScore", 5, "qualityScore", 5, "speedScore", 5,
                        "content", "维修员越权评价")))).isEqualTo(2004);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/evaluate",
                adminToken, Map.of("attitudeScore", 5, "qualityScore", 5, "speedScore", 5,
                        "content", "派单及时，流程顺畅")))).isEqualTo(200);
        assertThat(detailStatus(orderId)).isEqualTo("COMPLETED");
        // 重复评价被拒
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/evaluate",
                adminToken, Map.of("attitudeScore", 5, "qualityScore", 5, "speedScore", 5,
                        "content", "重复评价")))).isEqualTo(2004);

        // 时间线完整：CREATE / DISPATCH / REPAIR 事件 + 状态轨迹走完整状态机
        List<Map<String, Object>> nodes = dataOf(call(HttpMethod.GET,
                "/api/workorder/" + orderId + "/timeline", adminToken, null));
        assertThat(nodes).extracting(n -> String.valueOf(n.get("eventType")))
                .containsSubsequence("CREATE", "DISPATCH", "REPAIR");
        List<String> toStatuses = jdbcTemplate.queryForList(
                "SELECT to_status FROM wo_status_log WHERE work_order_id = ? ORDER BY id",
                String.class, orderId);
        assertThat(toStatuses).containsSubsequence(
                "PENDING_ASSIGN", "PENDING_ACCEPT", "IN_PROGRESS", "PENDING_REVIEW", "COMPLETED");

        // 已完结工单不能再次验收（非法状态跳转拦截）
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/verify-pass",
                adminToken, Map.of("qualityScore", 5, "remark", "重复验收应被拒绝")))).isEqualTo(3001);
    }

    /* ------------------------------------------------------------------ */
    /* 流程四：备件                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(4)
    @DisplayName("流程四：入库 → 工单领料 → 退料 → 库存与流水一致")
    void flow4Inventory() {
        Long orderId = createOrderAndAssign("十二阶段: 备件领用");

        // 入库 +5：20 → 25
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/stock/in", adminToken,
                Map.of("partId", partId, "quantity", 5, "remark", "阶段十二采购到货")))).isEqualTo(200);
        assertThat(stockOf(partId)).isEqualTo(25);

        // 领料 4 → 出库 21 → 退料 25
        Long applyId = createdId(call(HttpMethod.POST, "/api/inventory/applies", adminToken,
                Map.of("workOrderId", orderId, "partId", partId, "quantity", 4)));
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/issue",
                adminToken, null))).isEqualTo(200);
        assertThat(stockOf(partId)).isEqualTo(21);
        assertThat(spareCostOf(orderId).doubleValue()).isEqualTo(400.0);
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/return",
                adminToken, null))).isEqualTo(200);
        assertThat(stockOf(partId)).isEqualTo(25);

        // 数据一致性：流水 after_stock 与台账库存一致，且全程无负库存
        Integer ledgerAfter = jdbcTemplate.queryForObject(
                "SELECT after_stock FROM sp_stock_txn WHERE part_id = ? "
                        + "ORDER BY id DESC LIMIT 1", Integer.class, partId);
        assertThat(ledgerAfter).isEqualTo(25);
        Integer negativeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sp_stock_txn WHERE part_id = ? AND after_stock < 0",
                Integer.class, partId);
        assertThat(negativeCount).isZero();
    }

    /* ------------------------------------------------------------------ */
    /* 流程五 + Q-01/Q-02 回归：权限                                       */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(5)
    @DisplayName("流程五：受限用户可用接口正常，越权接口一律 403")
    void flow5PermissionBoundary() {
        // 允许：设备查看
        assertThat(codeOf(call(HttpMethod.GET, "/api/asset/equipment?pageNum=1&pageSize=5",
                viewerToken, null))).isEqualTo(200);
        // 允许：我的消息（登录级接口）
        assertThat(codeOf(call(HttpMethod.GET, "/api/system/messages?pageNum=1&pageSize=5",
                viewerToken, null))).isEqualTo(200);

        // 越权：用户管理（带合法负载确保进入 @PreAuthorize）/ 角色管理 / 工单列表 / 备件出库 / 盘点调整
        assertThat(codeOf(call(HttpMethod.POST, "/api/system/users", viewerToken,
                Map.of("username", "hacker12", "password", "Abcd1234!", "realName", "越权",
                        "roleIds", List.of(999999L))))).isEqualTo(403);
        assertThat(codeOf(call(HttpMethod.GET, "/api/system/roles", viewerToken, null))).isEqualTo(403);
        assertThat(codeOf(call(HttpMethod.GET, "/api/workorder?pageNum=1&pageSize=5",
                viewerToken, null))).isEqualTo(403);
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/stock/out", viewerToken,
                Map.of("partId", partId, "quantity", 1)))).isEqualTo(403);
        // 盘点调整：参数放 URL（@RequestParam + @Validated），合法参数下越权应 403
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/stock/adjust?partId=" + partId
                + "&quantity=1&positive=true", viewerToken, null))).isEqualTo(403);
    }

    @Test
    @Order(6)
    @DisplayName("Q-01 回归：文件下载按上传人/管理员/业务域权限放行，越权 403")
    void fileDownloadPermission() throws Exception {
        Long fileId = uploadFile(adminToken, "stage12.png", "WORK_ORDER");
        String downloadUrl = "/api/files/" + fileId;

        // 上传人本人：放行
        assertThat(downloadStatus(downloadUrl, adminToken)).isEqualTo(HttpStatus.OK);
        // 管理员（非上传人）：放行
        assertThat(downloadStatus(downloadUrl, repairToken) == HttpStatus.OK
                || downloadStatus(downloadUrl, repairToken) == HttpStatus.FORBIDDEN)
                .as("维修员持 wo:workorder:view 应放行").isTrue();
        assertThat(downloadStatus(downloadUrl, repairToken)).isEqualTo(HttpStatus.OK);
        // 受限用户（无 wo:workorder:view）：拒绝
        assertThat(downloadStatus(downloadUrl, viewerToken)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(7)
    @DisplayName("Q-01 回归：上传 bizType 白名单与业务权限校验")
    void fileUploadWhitelist() throws Exception {
        // 非法 bizType 拒绝
        assertThat(codeOf(uploadResult(adminToken, "stage12.png", "HACKED"))).isEqualTo(400);
        // 无业务权限的用户上传被拒
        assertThat(codeOf(uploadResult(viewerToken, "stage12.png", "WORK_ORDER"))).isEqualTo(403);
        // 合法上传成功
        assertThat(codeOf(uploadResult(adminToken, "stage12.png", "WORK_ORDER"))).isEqualTo(200);
    }

    @Test
    @Order(8)
    @DisplayName("Q-02 回归：盘点调整数量必须为正整数，负数 / 零拒绝")
    void adjustQuantityValidation() {
        int before = stockOf(partId);
        // 负数 + positive=true：旧行为会绕过防负库存闸，必须被拒（@Min 违反 → 422）
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/stock/adjust?partId=" + partId
                + "&quantity=-5&positive=true", adminToken, null))).isEqualTo(422);
        // 零被拒
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/stock/adjust?partId=" + partId
                + "&quantity=0&positive=true", adminToken, null))).isEqualTo(422);
        // 正常盘盈
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/stock/adjust?partId=" + partId
                + "&quantity=3&positive=true", adminToken, null))).isEqualTo(200);
        assertThat(stockOf(partId)).isEqualTo(before + 3);
        // 正常盘亏
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/stock/adjust?partId=" + partId
                + "&quantity=2&positive=false", adminToken, null))).isEqualTo(200);
        assertThat(stockOf(partId)).isEqualTo(before + 1);
    }

    /* ------------------------------------------------------------------ */
    /* 基础设施                                                            */
    /* ------------------------------------------------------------------ */

    private String detailStatus(Long orderId) {
        Map<String, Object> detail = dataOf(call(HttpMethod.GET,
                "/api/workorder/" + orderId, adminToken, null));
        return (String) detail.get("status");
    }

    private Long createOrderAndAssign(String title) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentId", equipmentId);
        payload.put("title", title);
        payload.put("faultType", "MECHANICAL");
        payload.put("faultDesc", "阶段十二集成测试自动创建的工单");
        payload.put("level", "P3");
        Long orderId = createdId(call(HttpMethod.POST, "/api/workorder", adminToken, payload));
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/dispatch", adminToken,
                Map.of("assigneeId", repairUserId, "reason", "测试派单")))).isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/accept",
                repairToken, null))).isEqualTo(200);
        return orderId;
    }

    private Long createPart(String partNo, String name, BigDecimal price, int stock) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("partNo", partNo);
        payload.put("partName", name);
        payload.put("unit", "个");
        payload.put("safetyStock", 2);
        payload.put("unitPrice", price);
        payload.put("status", 1);
        Long partId = createdId(call(HttpMethod.POST, "/api/inventory/parts", adminToken, payload));
        jdbcTemplate.update("UPDATE sp_part SET current_stock = ? WHERE id = ?", stock, partId);
        return partId;
    }

    private Long createEquipment(String name, String assetNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentName", name);
        payload.put("categoryId", categoryId);
        payload.put("locationId", locationId);
        payload.put("assetNo", assetNo);
        ResponseEntity<Result> response = call(HttpMethod.POST, "/api/asset/equipment", adminToken, payload);
        assertThat(codeOf(response)).isEqualTo(200);
        return ((Number) response.getBody().getData()).longValue();
    }

    private Long uploadFile(String token, String fileName, String bizType) throws Exception {
        ResponseEntity<Result> upload = uploadResult(token, fileName, bizType);
        assertThat(codeOf(upload)).isEqualTo(200);
        return ((Number) ((Map<String, Object>) upload.getBody().getData()).get("fileId")).longValue();
    }

    private ResponseEntity<Result> uploadResult(String token, String fileName, String bizType) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] png = out.toByteArray();

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        form.add("bizType", bizType);
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.postForEntity("/api/files/upload", new HttpEntity<>(form, headers), Result.class);
    }

    private org.springframework.http.HttpStatusCode downloadStatus(String path, String token) {
        HttpHeaders headers = authHeaders(token);
        ResponseEntity<byte[]> resp = restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
        return resp.getStatusCode();
    }

    private Map<String, Object> item(String name, String type, Map<String, Object> extra) {
        Map<String, Object> item = new HashMap<>();
        item.put("itemName", name);
        item.put("itemType", type);
        item.put("required", 1);
        item.put("abnormalLevel", "MEDIUM");
        item.put("autoWorkOrder", 0);
        item.put("photoRequired", 0);
        item.putAll(extra);
        return item;
    }

    private Long ensureRole(String code, String name) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_role WHERE role_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        if (id != null) {
            return id.longValue();
        }
        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, data_scope, builtin, sort, status) "
                + "VALUES (?, ?, 'ALL', 0, 95, 1)", code, name);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, code);
    }

    private void grantPermission(Long roleId, String code) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_permission WHERE perm_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        Long permId;
        if (id == null) {
            jdbcTemplate.update("INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, "
                    + "sort, visible, status) VALUES (0, ?, ?, 'API', 97, 1, 1)", code, code);
            permId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_permission WHERE perm_code = ?", Long.class, code);
        } else {
            permId = id.longValue();
        }
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, permId);
    }

    private Long createUser(String username, String password, String realName, Long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user (username, password, real_name, status) VALUES (?, ?, ?, 1)",
                username, passwordEncoder.encode(password), realName);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?",
                Long.class, username);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    private String loginToken(String username, String password) {
        ResponseEntity<Result> response = restTemplate.postForEntity("/api/auth/login",
                Map.of("username", username, "password", password), Result.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) ((Map<String, Object>) response.getBody().getData()).get("accessToken");
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

    private <T> T dataOf(ResponseEntity<Result> response) {
        assertThat(codeOf(response)).isEqualTo(200);
        return (T) response.getBody().getData();
    }

    private Long createdId(ResponseEntity<Result> response) {
        return ((Number) dataOf(response)).longValue();
    }

    private int stockOf(Long partId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_stock FROM sp_part WHERE id = ?", Integer.class, partId);
    }

    private BigDecimal spareCostOf(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT spare_cost FROM wo_work_order WHERE id = ?", BigDecimal.class, orderId);
    }
}
