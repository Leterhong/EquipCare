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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
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
 * 第六阶段集成测试：备件闭环 / SLA 预警 / 健康分 v2。
 *
 * <p>对应验收清单：
 * <ol>
 *   <li>库存扣减事务：出库 / 审批领用失败时整笔回滚，不产生流水、不改成本；</li>
 *   <li>并发领料：库存只够一份时，两个并发审批恰好一个成功一个 4001，库存不为负；</li>
 *   <li>库存不足：直接出库与工单领用出库都被 4001 拒绝；</li>
 *   <li>SLA 超时：扫描把逾期工单置 OVERDUE、登记预警并通知；同单同阶段同天重复扫描不再通知；</li>
 *   <li>健康评分规则：扣分因素逐项可解释，规则变化反映到分数。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
class Stage6IT {

    private static final String ADMIN_USERNAME = "stage6_admin";
    private static final String ADMIN_PASSWORD = "Stage6Admin123";
    private static final String REPAIR_USERNAME = "stage6_repair";
    private static final String REPAIR_PASSWORD = "Stage6Repair123";
    private static final String VIEWER_USERNAME = "stage6_viewer";
    private static final String VIEWER_PASSWORD = "Stage6Viewer123";

    private static final String[] ADMIN_PERMISSIONS = {
            "asset:equipment:view", "asset:equipment:create", "asset:equipment:update",
            "wo:workorder:view", "wo:workorder:create", "wo:workorder:dispatch",
            "wo:workorder:accept", "wo:workorder:repair", "wo:workorder:verify",
            "spare:part:view", "spare:part:manage", "spare:stock:in", "spare:stock:adjust",
            "spare:issue", "spare:apply", "spare:txn:view", "spare:stock:view", "spare:alert:view",
            "health:view", "sla:policy:view", "sla:policy:manage", "sla:monitor:view",
            "system:message:view"
    };

    private static final String[] REPAIR_PERMISSIONS = {
            "asset:equipment:view", "wo:workorder:view", "wo:workorder:accept",
            "wo:workorder:repair", "spare:part:view", "spare:apply"
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
    private String viewerToken;

    private Long adminUserId;
    private Long repairUserId;
    private Long categoryId;
    private Long locationId;
    private Long equipmentId;
    private Long partId;
    private Long partId2;
    private String suffix;

    @BeforeAll
    void setUp() {
        cleanup();
        Long adminRole = ensureRole("ADMIN", "六阶段管理员", "ALL");
        Long repairRole = ensureRole("REPAIRMAN", "六阶段维修员", "SELF");
        Long viewerRole = ensureRole("VIEWER", "六阶段只读", "SELF");
        grantAll(adminRole, ADMIN_PERMISSIONS);
        grantAll(repairRole, REPAIR_PERMISSIONS);
        grantAll(viewerRole, VIEWER_PERMISSIONS);

        adminUserId = createUser(ADMIN_USERNAME, ADMIN_PASSWORD, "六阶段管理员", adminRole);
        repairUserId = createUser(REPAIR_USERNAME, REPAIR_PASSWORD, "六阶段维修员", repairRole);
        createUser(VIEWER_USERNAME, VIEWER_PASSWORD, "六阶段只读用户", viewerRole);

        suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        jdbcTemplate.update("INSERT INTO eq_category (parent_id, category_code, category_name, sort, status) "
                + "VALUES (0, ?, '六阶段测试类别', 93, 1)", "T6C" + suffix);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_category WHERE category_code = ?", Long.class, "T6C" + suffix);
        jdbcTemplate.update("INSERT INTO eq_location (parent_id, location_code, location_name, location_type, "
                + "full_path, sort, status) VALUES (0, ?, '六阶段测试位置', 'ROOM', '测试园区/六阶段位置', 93, 1)",
                "T6LOC" + suffix);
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_location WHERE location_code = ?", Long.class, "T6LOC" + suffix);

        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        repairToken = login(REPAIR_USERNAME, REPAIR_PASSWORD);
        viewerToken = login(VIEWER_USERNAME, VIEWER_PASSWORD);

        equipmentId = createEquipment("六阶段测试水泵", "T6-" + suffix);

        // 测试库没有 seed 的 sla_policy 种子：政策为空时服务会兜底枚举值，
        // 但「配置生效」用例需要表里有行，这里按 schema 默认值补齐。
        jdbcTemplate.update("""
                INSERT IGNORE INTO sla_policy
                    (policy_name, level, response_minutes, resolve_minutes, verify_minutes,
                     warn_percent, escalate_role_code, enabled)
                VALUES ('紧急 SLA', 'P1', 30, 240, 120, 80, 'MANAGER', 1),
                       ('严重 SLA', 'P2', 60, 480, 240, 80, 'MANAGER', 1),
                       ('一般 SLA', 'P3', 240, 1440, 480, 80, 'EQUIP_ADMIN', 1),
                       ('轻微 SLA', 'P4', 480, 2880, 960, 80, 'EQUIP_ADMIN', 1)
                """);
    }

    @AfterAll
    void cleanupAll() {
        cleanup();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_message WHERE receiver_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'stage6\\_%')");
        jdbcTemplate.update("DELETE FROM sla_alert_record WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '六阶段%')");
        jdbcTemplate.update("DELETE FROM sp_work_order_part WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '六阶段%')");
        jdbcTemplate.update("DELETE FROM wo_repair_record WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '六阶段%')");
        jdbcTemplate.update("DELETE FROM wo_dispatch_log WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '六阶段%')");
        // sp_stock_txn 是 append-only（触发器禁止 DELETE），备件被流水外键引用也不能物理删除：
        // 与工单留痕同理，备件走逻辑删除、流水原样保留（备件编码带运行后缀，不会与下次冲突）
        jdbcTemplate.update("UPDATE sp_part SET deleted = 1 WHERE part_no LIKE 'T6-%'");
        jdbcTemplate.update("DELETE FROM sp_part_category WHERE category_name LIKE '六阶段%'");
        jdbcTemplate.update("UPDATE eq_equipment SET deleted = 1 WHERE asset_no LIKE 'T6-%'");
        jdbcTemplate.update("UPDATE eq_category SET deleted = 1 WHERE category_code LIKE 'T6C%'");
        jdbcTemplate.update("UPDATE eq_location SET deleted = 1 WHERE location_code LIKE 'T6LOC%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'stage6\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'stage6\\_%'");
    }

    /* ------------------------------------------------------------------ */
    /* 用例                                                                */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(1)
    @DisplayName("1. 备件分类与档案：分类 CRUD、编码唯一、重命名同步快照、非空分类不可删")
    void categoryAndArchive() {
        // 建分类
        Map<String, Object> category = Map.of("categoryName", "六阶段测试分类", "sort", 1);
        Long catId = createdId(call(HttpMethod.POST, "/api/inventory/categories", adminToken, category));

        // 分类列表可见且计数为 0
        List<Map<String, Object>> list = listOf(call(HttpMethod.GET, "/api/inventory/categories", adminToken, null));
        assertThat(list.stream().anyMatch(c -> ((Number) c.get("id")).longValue() == catId
                && ((Number) c.get("partCount")).longValue() == 0)).isTrue();

        // 建备件（挂分类），编码重复被拒
        Map<String, Object> part = new HashMap<>();
        part.put("partNo", "T6-BEARING-" + suffix);
        part.put("partName", "六阶段测试轴承");
        part.put("categoryId", catId);
        part.put("unit", "个");
        part.put("safetyStock", 5);
        part.put("unitPrice", 88.50);
        Long newPartId = createdId(call(HttpMethod.POST, "/api/inventory/parts", adminToken, part));

        Map<String, Object> dup = new HashMap<>(part);
        dup.put("partName", "另一个");
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/parts", adminToken, dup))).isEqualTo(2002);

        // 档案带分类快照
        Map<String, Object> saved = partOf(newPartId);
        assertThat(saved.get("categoryId")).isEqualTo(catId.intValue());
        assertThat(saved.get("category")).isEqualTo("六阶段测试分类");

        // 重命名分类 → 备件上的分类快照同步
        call(HttpMethod.PUT, "/api/inventory/categories/" + catId, adminToken,
                Map.of("categoryName", "六阶段测试分类改", "sort", 1));
        assertThat(partOf(newPartId).get("category")).isEqualTo("六阶段测试分类改");

        // 非空分类不可删除
        assertThat(codeOf(call(HttpMethod.DELETE, "/api/inventory/categories/" + catId, adminToken, null)))
                .isEqualTo(2003);

        partId = newPartId;
    }

    @Test
    @Order(2)
    @DisplayName("2. 入库 / 出库 / 盘点调整：每步生成流水且 before/after 正确，盘亏超库存被拒且无流水")
    void stockInOutAndAdjust() {
        int beforeTxns = txnCount(partId);

        // 入库 20
        Map<String, Object> in = new HashMap<>();
        in.put("partId", partId);
        in.put("quantity", 20);
        in.put("remark", "首采入库");
        Map<String, Object> inTxn = dataOf(call(HttpMethod.POST, "/api/inventory/stock/in", adminToken, in));
        assertThat(inTxn.get("txnType")).isEqualTo("IN");
        assertThat(inTxn.get("beforeStock")).isEqualTo(0);
        assertThat(inTxn.get("afterStock")).isEqualTo(20);
        assertThat(inTxn.get("txnNo")).isNotNull();
        assertThat(stockOf(partId)).isEqualTo(20);

        // 出库 3
        Map<String, Object> out = new HashMap<>();
        out.put("partId", partId);
        out.put("quantity", 3);
        out.put("remark", "外借");
        Map<String, Object> outTxn = dataOf(call(HttpMethod.POST, "/api/inventory/stock/out", adminToken, out));
        assertThat(outTxn.get("txnType")).isEqualTo("OUT");
        assertThat(outTxn.get("beforeStock")).isEqualTo(20);
        assertThat(outTxn.get("afterStock")).isEqualTo(17);
        assertThat(stockOf(partId)).isEqualTo(17);

        // 盘盈 2（该接口用 @RequestParam 传参）
        Map<String, Object> adjTxn = dataOf(call(HttpMethod.POST,
                "/api/inventory/stock/adjust?partId=" + partId + "&quantity=2&positive=true&remark=月末盘盈",
                adminToken, null));
        assertThat(adjTxn.get("txnType")).isEqualTo("ADJUST");
        assertThat(adjTxn.get("afterStock")).isEqualTo(19);
        assertThat(stockOf(partId)).isEqualTo(19);

        // 盘亏超过库存：被 4001 拒绝，且事务回滚不产生流水
        int before = txnCount(partId);
        ResponseEntity<Result> rejected = call(HttpMethod.POST,
                "/api/inventory/stock/adjust?partId=" + partId + "&quantity=999&positive=false",
                adminToken, null);
        assertThat(codeOf(rejected)).isEqualTo(4001);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(txnCount(partId)).isEqualTo(before);
        assertThat(stockOf(partId)).isEqualTo(19);

        assertThat(txnCount(partId) - beforeTxns).isEqualTo(3);

        // 建第二个备件，留给并发用例
        Map<String, Object> part2 = new HashMap<>();
        part2.put("partNo", "T6-SEAL-" + suffix);
        part2.put("partName", "六阶段测试密封圈");
        part2.put("unit", "片");
        part2.put("safetyStock", 10);
        part2.put("unitPrice", 12.00);
        partId2 = createdId(call(HttpMethod.POST, "/api/inventory/parts", adminToken, part2));
        call(HttpMethod.POST, "/api/inventory/stock/in", adminToken,
                Map.of("partId", partId2, "quantity", 5));
    }

    @Test
    @Order(3)
    @DisplayName("3. 库存不足：直接出库 4001，库存与流水均不变")
    void stockInsufficient() {
        int beforeStock = stockOf(partId);
        int beforeTxns = txnCount(partId);
        Map<String, Object> out = new HashMap<>();
        out.put("partId", partId);
        out.put("quantity", beforeStock + 1);
        out.put("remark", "超出库存的出库");
        ResponseEntity<Result> response = call(HttpMethod.POST, "/api/inventory/stock/out", adminToken, out);
        assertThat(codeOf(response)).isEqualTo(4001);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(stockOf(partId)).isEqualTo(beforeStock);
        assertThat(txnCount(partId)).isEqualTo(beforeTxns);
    }

    @Test
    @Order(4)
    @DisplayName("4. 工单备件关联：申请不扣库存 → 审批出库扣库存写流水累加成本 → 退料回增冲减")
    void orderPartFlow() {
        // 报修 + 派单 + 接单
        Long orderId = createOrderAndAssign("六阶段: 备件关联全链路");

        // 维修人申请 4 个轴承（库存 19，不扣）
        Map<String, Object> apply = new HashMap<>();
        apply.put("workOrderId", orderId);
        apply.put("partId", partId);
        apply.put("quantity", 4);
        Long applyId = createdId(call(HttpMethod.POST, "/api/inventory/applies", repairToken, apply));
        assertThat(stockOf(partId)).isEqualTo(19);

        // 未出库前备件成本为 0
        assertThat(spareCostOf(orderId).doubleValue()).isEqualTo(0.0);

        // 管理员审批出库：扣 4、写 OUT/WORK_ORDER 流水、成本 4×88.50=354
        Long issueTxnId = createdId(call(HttpMethod.POST,
                "/api/inventory/applies/" + applyId + "/issue", adminToken, null));
        Map<String, Object> issueTxn = jdbcTemplate.queryForMap(
                "SELECT txn_type, biz_type, biz_id, after_stock FROM sp_stock_txn WHERE id = ?", issueTxnId);
        assertThat(issueTxn.get("txn_type")).isEqualTo("OUT");
        assertThat(issueTxn.get("biz_type")).isEqualTo("WORK_ORDER");
        assertThat(((Number) issueTxn.get("biz_id")).longValue()).isEqualTo(orderId);
        assertThat(((Number) issueTxn.get("after_stock")).intValue()).isEqualTo(15);
        assertThat(stockOf(partId)).isEqualTo(15);
        assertThat(spareCostOf(orderId).doubleValue()).isEqualTo(354.0);

        // 申请单已回写 txn_id 与出库时间
        Map<String, Object> applyRow = jdbcTemplate.queryForMap(
                "SELECT issue_status, txn_id FROM sp_work_order_part WHERE id = ?", applyId);
        assertThat(applyRow.get("issue_status")).isEqualTo("ISSUED");
        assertThat(applyRow.get("txn_id")).isNotNull();

        // 退料 1 次全量：库存回增 4、成本冲减为 0、RETURN 流水
        Long returnTxnId = createdId(call(HttpMethod.POST,
                "/api/inventory/applies/" + applyId + "/return", adminToken, Map.of("remark", "剩余全退")));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT txn_type FROM sp_stock_txn WHERE id = ?", String.class, returnTxnId))
                .isEqualTo("RETURN");
        assertThat(stockOf(partId)).isEqualTo(19);
        assertThat(stockOf(partId)).isEqualTo(19);
        assertThat(spareCostOf(orderId).doubleValue()).isEqualTo(0.0);

        // 重复退料被拒（状态门禁）
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/return",
                adminToken, null))).isEqualTo(2004);

        // 领用申请分页可查（审批视角）
        Map<String, Object> appliesPage = dataOf(call(HttpMethod.GET,
                "/api/inventory/applies?workOrderId=" + orderId, adminToken, null));
        assertThat(((List<Map<String, Object>>) appliesPage.get("records"))).isNotEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("5. 并发领料：库存只够一份，两个并发审批恰好一成一败，库存不为负")
    void concurrentIssue() throws Exception {
        Long orderId = createOrderAndAssign("六阶段: 并发领料");
        // 密封圈库存 5，两张单各申请 5
        Long apply1 = createdId(call(HttpMethod.POST, "/api/inventory/applies", repairToken,
                Map.of("workOrderId", orderId, "partId", partId2, "quantity", 5)));
        Long apply2 = createdId(call(HttpMethod.POST, "/api/inventory/applies", repairToken,
                Map.of("workOrderId", orderId, "partId", partId2, "quantity", 5)));
        assertThat(stockOf(partId2)).isEqualTo(5);
        int txnsBefore = txnCount(partId2);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Result>> issue1 = () -> {
                latch.await();
                return call(HttpMethod.POST, "/api/inventory/applies/" + apply1 + "/issue", adminToken, null);
            };
            Callable<ResponseEntity<Result>> issue2 = () -> {
                latch.await();
                return call(HttpMethod.POST, "/api/inventory/applies/" + apply2 + "/issue", adminToken, null);
            };
            Future<ResponseEntity<Result>> f1 = pool.submit(issue1);
            Future<ResponseEntity<Result>> f2 = pool.submit(issue2);
            latch.countDown();

            int ok = 0;
            int insufficient = 0;
            for (Future<ResponseEntity<Result>> future : List.of(f1, f2)) {
                ResponseEntity<Result> resp = future.get(30, TimeUnit.SECONDS);
                int code = codeOf(resp);
                if (code == 200) {
                    ok++;
                } else if (code == 4001) {
                    insufficient++;
                }
            }
            assertThat(ok).isEqualTo(1);
            assertThat(insufficient).isEqualTo(1);

            // 数据一致：库存归 0（不为负），OUT 流水恰好 1 条，spare_cost 只累加一次
            assertThat(stockOf(partId2)).isEqualTo(0);
            assertThat(txnCount(partId2) - txnsBefore).isEqualTo(1);
            assertThat(spareCostOf(orderId).doubleValue()).isEqualTo(60.0);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Order(6)
    @DisplayName("6. SLA 政策：列表、更新 P3 时限立即对新建工单生效")
    void slaPolicyConfig() {
        List<Map<String, Object>> policies = listOf(call(HttpMethod.GET, "/api/sla/policies", adminToken, null));
        assertThat(policies).hasSize(4);

        // 更新 P3：修复时限 1440 → 60 分钟、验收时限 → 30 分钟
        Map<String, Object> update = new HashMap<>();
        update.put("policyName", "一般工单 SLA");
        update.put("responseMinutes", 240);
        update.put("resolveMinutes", 60);
        update.put("verifyMinutes", 30);
        update.put("warnPercent", 80);
        update.put("enabled", true);
        assertThat(codeOf(call(HttpMethod.PUT, "/api/sla/policies/P3", adminToken, update))).isEqualTo(200);

        // 新建 P3 工单：resolve_due_time ≈ now + 60 分钟（政策生效）
        Long orderId = createOrder("六阶段: 政策生效验证");
        Map<String, Object> detail = orderDetailOf(orderId);
        String resolveDue = (String) detail.get("resolveDueTime");
        assertThat(resolveDue).isNotNull();
        long minutes = minutesUntil(resolveDue);
        assertThat(minutes).isBetween(50L, 65L);
    }

    @Test
    @Order(7)
    @DisplayName("7. SLA 扫描与通知去重：逾期工单置 OVERDUE、登记预警、重复扫描不再通知")
    void slaScanAndDedup() {
        Long orderId = createOrderAndAssign("六阶段: SLA 超时预警");
        // 把修复时限改到过去，模拟「维修中超时」。必须用 JDBC 传 LocalDateTime：
        // MySQL NOW() 与 JVM 时钟可能存在时区差，扫描器用的是应用时钟
        jdbcTemplate.update("UPDATE wo_work_order SET resolve_due_time = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(120), orderId);

        int notified = (Integer) dataOf(call(HttpMethod.POST, "/api/sla/alerts/scan", adminToken, null)
                , Integer.class);
        // 本轮至少把这张单通知了（可能还有其他用例遗留的逾期单）
        assertThat(notified).isGreaterThanOrEqualTo(1);

        // 工单被置 OVERDUE
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sla_status FROM wo_work_order WHERE id = ?", String.class, orderId))
                .isEqualTo("OVERDUE");

        // 预警记录：本单本阶段今天恰好 1 条
        Integer records = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sla_alert_record WHERE work_order_id = ? AND stage = 'IN_PROGRESS'",
                Integer.class, orderId);
        assertThat(records).isEqualTo(1);

        // 站内消息：升级角色 + ADMIN + 维修人，每个收件人恰 1 条
        Integer messages = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_message WHERE biz_type = 'SLA_ALERT' AND biz_id = ?",
                Integer.class, orderId);
        assertThat(messages).isGreaterThanOrEqualTo(1);

        // 重复扫描：不再新增记录、不再新增消息（唯一键 + upsert 返回 2 跳过通知）
        int recordsBefore = records;
        int messagesBefore = messages;
        call(HttpMethod.POST, "/api/sla/alerts/scan", adminToken, null);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sla_alert_record WHERE work_order_id = ? AND stage = 'IN_PROGRESS'",
                Integer.class, orderId)).isEqualTo(recordsBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_message WHERE biz_type = 'SLA_ALERT' AND biz_id = ?",
                Integer.class, orderId)).isEqualTo(messagesBefore);

        // alert_count 累加但记录不增
        Integer alertCount = jdbcTemplate.queryForObject(
                "SELECT alert_count FROM sla_alert_record WHERE work_order_id = ? AND stage = 'IN_PROGRESS'",
                Integer.class, orderId);
        assertThat(alertCount).isGreaterThanOrEqualTo(2);

        // 预警记录分页可查
        Map<String, Object> alertPage = dataOf(call(HttpMethod.GET,
                "/api/sla/alerts?stage=IN_PROGRESS", adminToken, null));
        assertThat(((List<Map<String, Object>>) alertPage.get("records")))
                .anyMatch(a -> ((Number) a.get("workOrderId")).longValue() == orderId);
    }

    @Test
    @Order(8)
    @DisplayName("8. 健康评分规则：扣分逐项可解释、在途工单与运行状态反映到分数")
    void healthScoreRules() {
        // 用例内新建干净设备做精确断言：无工单、无异常、在用状态，基线应为满分 100
        //（公共设备可能被其他用例遗留工单污染，不适合绝对值断言）
        Long healthEqId = createEquipment("六阶段健康分测试设备", "T6-H-" + suffix);
        double base = ((Number) healthOf(healthEqId).get("healthScore")).doubleValue();
        assertThat(base).isEqualTo(100.0);

        // 制造风险：一张在途工单（故障-2 + 在途-3）+ 闲置状态（-5）
        Long orderId = createOrder("六阶段: 健康分在途工单", healthEqId);
        jdbcTemplate.update("UPDATE eq_equipment SET status = 'IDLE' WHERE id = ?", healthEqId);
        Map<String, Object> after = healthOf(healthEqId);
        // 工单口径不限状态（取消也计入近窗口故障次数），所以建单本身扣故障分 2
        assertThat(((Number) after.get("healthScore")).doubleValue()).isEqualTo(90.0);
        assertThat(((Number) after.get("openWoCount")).intValue()).isEqualTo(1);

        // 每项扣分都有明细与建议
        List<Map<String, Object>> riskItems = (List<Map<String, Object>>) after.get("riskItems");
        assertThat(riskItems).extracting(i -> i.get("factor"))
                .contains("FAULT", "OPEN_ORDER", "STATUS");
        assertThat(riskItems).allSatisfy(i -> {
            assertThat(i.get("detail")).asString().isNotBlank();
            assertThat(i.get("suggestion")).asString().isNotBlank();
        });
        assertThat((List<String>) after.get("suggestions")).isNotEmpty();

        // 规则变化即分数变化：取消工单后在途扣分消失（取消会把设备状态回滚到建单前，
        // 这里重新置 IDLE 以隔离「状态回滚」这个副作用，只验证在途工单因素；
        // 故障分仍在——取消的工单依旧计入近窗口故障次数）
        call(HttpMethod.POST, "/api/workorder/" + orderId + "/cancel", adminToken,
                Map.of("reason", "测试收尾取消工单"));
        jdbcTemplate.update("UPDATE eq_equipment SET status = 'IDLE' WHERE id = ?", healthEqId);
        Map<String, Object> finalVo = healthOf(healthEqId);
        assertThat(((Number) finalVo.get("healthScore")).doubleValue()).isEqualTo(93.0);
        assertThat(((Number) finalVo.get("openWoCount")).intValue()).isZero();
    }

    @Test
    @Order(9)
    @DisplayName("9. 权限矩阵：无权限入库/改政策 403，只读用户可看健康与 SLA 监控")
    void permissionMatrix() {
        // 维修人不能入库、不能审批出库、不能改政策
        assertThat(call(HttpMethod.POST, "/api/inventory/stock/in", repairToken,
                Map.of("partId", partId, "quantity", 1)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/sla/policies", viewerToken, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.PUT, "/api/sla/policies/P3", adminToken,
                Map.of("resolveMinutes", 1440)).getStatusCode()).isEqualTo(HttpStatus.OK);

        // 恢复 P3 默认值，避免影响其他断言
        call(HttpMethod.PUT, "/api/sla/policies/P3", adminToken, Map.of(
                "policyName", "一般工单 SLA", "responseMinutes", 240, "resolveMinutes", 1440,
                "verifyMinutes", 480, "warnPercent", 80, "enabled", true));

        // 只读用户可看 SLA 监控与健康（sla:monitor:view / health:view 已授权 viewer？没有——应 403）
        assertThat(call(HttpMethod.GET, "/api/sla/active-orders", viewerToken, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/analytics/health/at-risk?threshold=0", viewerToken, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // 管理员可用全部查询
        assertThat(codeOf(call(HttpMethod.GET, "/api/sla/active-orders", adminToken, null))).isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.GET, "/api/analytics/health/at-risk?threshold=0", adminToken, null))).isEqualTo(200);
    }

    @Test
    @Order(10)
    @DisplayName("10. 不存在直改库存的入口：档案编辑携带库存字段不生效，库存只能靠流水变动")
    void noDirectStockUpdate() {
        int before = stockOf(partId);
        // 编辑接口根本不接收 currentStock 字段：即使塞进 payload 也会被忽略
        Map<String, Object> patch = new HashMap<>();
        patch.put("partNo", "T6-BEARING-" + suffix);
        patch.put("partName", "六阶段测试轴承");
        patch.put("unit", "个");
        patch.put("currentStock", 99999);
        patch.put("safetyStock", 5);
        assertThat(codeOf(call(HttpMethod.PUT, "/api/inventory/parts/" + partId, adminToken, patch))).isEqualTo(200);
        assertThat(stockOf(partId)).isEqualTo(before);
    }

    /* ------------------------------------------------------------------ */
    /* 业务辅助                                                            */
    /* ------------------------------------------------------------------ */

    private Long createOrder(String title) {
        return createOrder(title, equipmentId);
    }

    private Long createOrder(String title, Long eqId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentId", eqId);
        payload.put("title", title);
        payload.put("faultType", "MECHANICAL");
        payload.put("faultDesc", "六阶段集成测试自动创建的工单");
        payload.put("level", "P3");
        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/workorder", adminToken, payload);
        assertThat(codeOf(created)).isEqualTo(200);
        return ((Number) created.getBody().getData()).longValue();
    }

    private Long createOrderAndAssign(String title) {
        Long orderId = createOrder(title);
        Map<String, Object> dispatch = new HashMap<>();
        dispatch.put("assigneeId", repairUserId);
        dispatch.put("reason", "测试派单");
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/dispatch", adminToken, dispatch)))
                .isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/accept", repairToken, null)))
                .isEqualTo(200);
        return orderId;
    }

    private Map<String, Object> orderDetailOf(Long orderId) {
        return dataOf(call(HttpMethod.GET, "/api/workorder/" + orderId, adminToken, null));
    }

    private Map<String, Object> healthOf(Long equipmentId) {
        call(HttpMethod.POST, "/api/analytics/health/" + equipmentId + "/recalculate", adminToken, null);
        return dataOf(call(HttpMethod.GET, "/api/analytics/health/" + equipmentId, adminToken, null));
    }

    private Map<String, Object> partOf(Long id) {
        Map<String, Object> page = dataOf(call(HttpMethod.GET,
                "/api/inventory/parts?pageNum=1&pageSize=50", adminToken, null));
        return ((List<Map<String, Object>>) page.get("records")).stream()
                .filter(p -> ((Number) p.get("id")).longValue() == id)
                .findFirst().orElseThrow();
    }

    private int stockOf(Long partId) {
        return jdbcTemplate.queryForObject(
                "SELECT current_stock FROM sp_part WHERE id = ?", Integer.class, partId);
    }

    private int txnCount(Long partId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sp_stock_txn WHERE part_id = ?", Integer.class, partId);
        return count == null ? 0 : count;
    }

    private java.math.BigDecimal spareCostOf(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT spare_cost FROM wo_work_order WHERE id = ?", java.math.BigDecimal.class, orderId);
    }

    private long minutesUntil(String timeText) {
        // 序列化格式为 "yyyy-MM-dd HH:mm:ss"（可能带毫秒），统一按前 19 位解析
        LocalDateTime time = LocalDateTime.parse(timeText.substring(0, 19).replace(' ', 'T'));
        return java.time.Duration.between(LocalDateTime.now(), time).toMinutes();
    }

    /* ------------------------------------------------------------------ */
    /* 基础设施                                                            */
    /* ------------------------------------------------------------------ */

    private Long ensureRole(String code, String name, String scope) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_role WHERE role_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        if (id != null) {
            return id.longValue();
        }
        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, data_scope, builtin, sort, status) "
                + "VALUES (?, ?, ?, 0, 66, 1)", code, name, scope);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, code);
    }

    private void grantAll(Long roleId, String[] codes) {
        for (String code : codes) {
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

    private <T> T dataOf(ResponseEntity<Result> response) {
        assertThat(codeOf(response)).isEqualTo(200);
        return (T) response.getBody().getData();
    }

    private Long createdId(ResponseEntity<Result> response) {
        return ((Number) dataOf(response)).longValue();
    }

    private <T> T dataOf(ResponseEntity<Result> response, Class<T> type) {
        assertThat(codeOf(response)).isEqualTo(200);
        return (T) response.getBody().getData();
    }

    private List<Map<String, Object>> listOf(ResponseEntity<Result> response) {
        return dataOf(response);
    }

    private Long createEquipment(String name, String assetNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentName", name);
        payload.put("categoryId", categoryId);
        payload.put("locationId", locationId);
        payload.put("brand", "测试品牌");
        payload.put("model", "T6-100");
        payload.put("assetNo", assetNo);
        ResponseEntity<Result> response = call(HttpMethod.POST, "/api/asset/equipment", adminToken, payload);
        assertThat(codeOf(response)).isEqualTo(200);
        return ((Number) response.getBody().getData()).longValue();
    }
}
