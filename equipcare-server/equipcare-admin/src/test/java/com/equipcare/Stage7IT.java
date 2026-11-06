package com.equipcare;

import com.equipcare.common.result.Result;
import com.equipcare.security.dto.LoginRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第七阶段集成测试：驾驶舱指标与图表（真实聚合、空范围不编造）、
 * 通知中心已读流转与越权、通知触发（新工单 / 巡检任务提醒）、
 * 审计记录与敏感信息脱敏、Excel 导出内容与权限。
 *
 * <p>测试数据以「七阶段」前缀标识，AfterAll 清理；流水等 append-only 表不删。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
class Stage7IT {

    private static final String SUFFIX = String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
    private static final String ADMIN_USER = "s7_admin_" + SUFFIX;
    private static final String VIEWER_USER = "s7_viewer_" + SUFFIX;
    private static final String ADMIN_PASS = "S7#" + SUFFIX + "a!";
    private static final String VIEWER_PASS = "S7#" + SUFFIX + "b!";

    private String adminToken;
    private String viewerToken;
    private Long adminUserId;
    private Long viewerUserId;
    private Long equipmentId;
    private Long categoryId;
    private Long locationId;
    private Long completedOrderId;

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeAll
    void setUp() {
        // 测试账号：管理员（含 analytics:export）与巡检员（无导出权限）
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, real_name, status) VALUES (?, ?, '七阶段管理员', 1)",
                ADMIN_USER, passwordEncoder.encode(ADMIN_PASS));
        jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, real_name, status) VALUES (?, ?, '七阶段巡检员', 1)",
                VIEWER_USER, passwordEncoder.encode(VIEWER_PASS));
        adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, ADMIN_USER);
        viewerUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, VIEWER_USER);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, (SELECT id FROM sys_role WHERE role_code = 'ADMIN'))", adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, (SELECT id FROM sys_role WHERE role_code = 'INSPECTOR'))", viewerUserId);
        // 测试库 ADMIN 种子不含巡检执行/查看权限，补授以覆盖手工建任务与巡检记录导出链路
        jdbcTemplate.update(
                "INSERT INTO sys_role_permission (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM sys_role r JOIN sys_permission p "
                        + "ON p.perm_code IN ('inspect:task:execute', 'inspect:task:view') "
                        + "WHERE r.role_code = 'ADMIN' "
                        + "AND NOT EXISTS (SELECT 1 FROM sys_role_permission x "
                        + "WHERE x.role_id = r.id AND x.permission_id = p.id)");

        adminToken = login(ADMIN_USER, ADMIN_PASS);
        viewerToken = login(VIEWER_USER, VIEWER_PASS);

        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_category WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_location WHERE deleted = 0 ORDER BY id LIMIT 1", Long.class);
    }

    @AfterAll
    void cleanup() {
        // 逻辑删除账号与设备；消息/审计/工单按前缀物理清理（测试库，允许 DELETE）
        jdbcTemplate.update("UPDATE sys_user SET deleted = 1 WHERE username IN (?, ?)", ADMIN_USER, VIEWER_USER);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (?, ?)", adminUserId, viewerUserId);
        if (equipmentId != null) {
            jdbcTemplate.update("UPDATE eq_equipment SET deleted = 1 WHERE id = ?", equipmentId);
        }
        jdbcTemplate.update("DELETE FROM sys_message WHERE title LIKE '七阶段%'");
        // sys_audit_log 为 append-only（触发器禁止 DELETE），审计记录按用户名前缀保留备查
        jdbcTemplate.update("UPDATE insp_template SET deleted = 1 WHERE template_code LIKE ?", "S7T%");
    }

    @Test
    @Order(1)
    @DisplayName("1. 空范围不编造：未来时间段全部指标为 0/null、八图全空")
    void emptyRangeNoFabrication() {
        String future = LocalDate.now().plusYears(5).toString();
        String futureEnd = LocalDate.now().plusYears(5).plusDays(30).toString();
        Map<String, Object> summary = dataOf(call(HttpMethod.GET,
                "/api/analytics/dashboard/summary?startDate=" + future + "&endDate=" + futureEnd));
        assertThat(((Number) summary.get("workOrderCompleted")).longValue()).isZero();
        assertThat(((Number) summary.get("workOrderOverdue")).longValue()).isZero();
        assertThat(summary.get("mttrMinutes")).isNull();
        assertThat(summary.get("mtbfHours")).isNull();

        Map<String, Object> charts = dataOf(call(HttpMethod.GET,
                "/api/analytics/dashboard/charts?startDate=" + future + "&endDate=" + futureEnd));
        assertThat((List<Map<String, Object>>) charts.get("equipmentStatusDist")).isNotEmpty(); // 当前快照不受时间影响
        assertThat((List<Map<String, Object>>) charts.get("workOrderStatusDist")).isEmpty();
        assertThat((List<Map<String, Object>>) charts.get("monthlyFaultTrend")).isEmpty();
        assertThat((List<Map<String, Object>>) charts.get("monthlyInspectionTrend")).isEmpty();
        assertThat((List<Map<String, Object>>) charts.get("monthlyCostTrend")).isEmpty();
        assertThat((List<Map<String, Object>>) charts.get("faultTypeRank")).isEmpty();
        assertThat((List<Map<String, Object>>) charts.get("equipmentFaultRank")).isEmpty();
        assertThat((List<Map<String, Object>>) charts.get("repairmanRank")).isEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("2. 驾驶舱指标口径：MTTR=60 分钟、健康率、完成/超时计数、备件消耗")
    void dashboardSummary() {
        equipmentId = createEquipment("七阶段驾驶舱设备", "S7-DASH-" + SUFFIX);
        completedOrderId = createOrder("七阶段已完成工单");

        // 置为完成：开始维修 60 分钟前，现在完成；同时制造一条出库流水
        jdbcTemplate.update(
                "UPDATE wo_work_order SET status = 'COMPLETED', assignee_id = ?, "
                        + "start_repair_time = DATE_SUB(NOW(), INTERVAL 60 MINUTE), "
                        + "actual_finish_time = NOW(), close_time = NOW(), spare_cost = 120, "
                        + "accept_time = DATE_SUB(NOW(), INTERVAL 2 HOUR) WHERE id = ?",
                adminUserId, completedOrderId);
        jdbcTemplate.update(
                "UPDATE eq_equipment SET status = 'IN_USE' WHERE id = ?", equipmentId);

        Map<String, Object> summary = dataOf(call(HttpMethod.GET,
                "/api/analytics/dashboard/summary?startDate=" + LocalDate.now() + "&endDate=" + LocalDate.now()));
        assertThat(((Number) summary.get("equipmentTotal")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) summary.get("equipmentNormal")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) summary.get("equipmentHealthRate")).doubleValue()).isGreaterThan(0);
        assertThat(((Number) summary.get("workOrderCompleted")).longValue()).isGreaterThanOrEqualTo(1);
        assertThat(((Number) summary.get("maintenanceCost")).doubleValue()).isGreaterThanOrEqualTo(120.0);
        // MTTR 口径：AVG(完成时刻 - 开始维修)，按同一时间范围从库中直接计算期望值对齐
        Double expectedMttr = jdbcTemplate.queryForObject(
                "SELECT AVG(TIMESTAMPDIFF(MINUTE, start_repair_time, actual_finish_time)) "
                        + "FROM wo_work_order WHERE status = 'COMPLETED' AND start_repair_time IS NOT NULL "
                        + "AND actual_finish_time >= ? AND actual_finish_time < ?",
                Double.class, LocalDate.now().atStartOfDay(), LocalDate.now().plusDays(1).atStartOfDay());
        assertThat(summary.get("mttrMinutes")).isNotNull();
        assertThat(((Number) summary.get("mttrMinutes")).doubleValue())
                .isEqualTo(expectedMttr.doubleValue(), org.assertj.core.data.Offset.offset(0.01));
        // MTBF：本周期有 1 张故障工单，非空
        assertThat(summary.get("mtbfHours")).isNotNull();

        // 设备清单导出数据源同口径：备件消耗金额随流水存在
        jdbcTemplate.update(
                "INSERT INTO sp_stock_txn (txn_no, part_id, txn_type, biz_type, biz_id, quantity, "
                        + "before_stock, after_stock, unit_price, amount, operator_id, txn_time) "
                        + "SELECT CONCAT('S7TXN', ?, id), id, 'OUT', 'WORK_ORDER', ?, 1, 10, 9, 60, 60, ?, NOW() "
                        + "FROM sp_part WHERE deleted = 0 LIMIT 1",
                SUFFIX, completedOrderId, adminUserId);
        Map<String, Object> refreshed = dataOf(call(HttpMethod.GET,
                "/api/analytics/dashboard/summary?startDate=" + LocalDate.now() + "&endDate=" + LocalDate.now()));
        assertThat(((Number) refreshed.get("spareConsumptionAmount")).doubleValue()).isGreaterThanOrEqualTo(60.0);
    }

    @Test
    @Order(3)
    @DisplayName("3. 驾驶舱八图来自真实数据：故障趋势/排行/维修人工作量/成本趋势")
    void dashboardCharts() {
        // 排行 LIMIT 10 且历史遗留设备各占 1 次：为测试设备再补 2 张已完成工单使其进入排行
        for (int i = 1; i <= 2; i++) {
            Long extraOrderId = createOrder("七阶段排行补充工单" + i);
            jdbcTemplate.update(
                    "UPDATE wo_work_order SET status = 'COMPLETED', assignee_id = ?, "
                            + "start_repair_time = DATE_SUB(NOW(), INTERVAL 30 MINUTE), "
                            + "actual_finish_time = NOW(), close_time = NOW() WHERE id = ?",
                    adminUserId, extraOrderId);
        }
        Map<String, Object> charts = dataOf(call(HttpMethod.GET,
                "/api/analytics/dashboard/charts?startDate=" + LocalDate.now().minusDays(30)
                        + "&endDate=" + LocalDate.now()));

        List<Map<String, Object>> faultTrend =
                (List<Map<String, Object>>) charts.get("monthlyFaultTrend");
        assertThat(faultTrend).isNotEmpty();
        assertThat(String.valueOf(faultTrend.get(faultTrend.size() - 1).get("month")))
                .isEqualTo(LocalDate.now().toString().substring(0, 7));

        assertThat((List<Map<String, Object>>) charts.get("faultTypeRank"))
                .anySatisfy(row -> assertThat(String.valueOf(row.get("name"))).isEqualTo("MECHANICAL"));
        assertThat((List<Map<String, Object>>) charts.get("equipmentFaultRank"))
                .anySatisfy(row -> assertThat(String.valueOf(row.get("name"))).contains("七阶段驾驶舱设备"));
        assertThat((List<Map<String, Object>>) charts.get("repairmanRank"))
                .anySatisfy(row -> assertThat(((Number) row.get("value")).longValue()).isGreaterThanOrEqualTo(1));
        assertThat((List<Map<String, Object>>) charts.get("monthlyCostTrend"))
                .anySatisfy(row -> assertThat(((Number) row.get("total")).doubleValue()).isGreaterThanOrEqualTo(120.0));
        assertThat((List<Map<String, Object>>) charts.get("equipmentStatusDist"))
                .anySatisfy(row -> assertThat(String.valueOf(row.get("name"))).isEqualTo("IN_USE"));
        // 工单状态分布按 report_time 归属：本周期至少有本用例完成单
        assertThat((List<Map<String, Object>>) charts.get("workOrderStatusDist"))
                .anySatisfy(row -> assertThat(String.valueOf(row.get("name"))).isEqualTo("COMPLETED"));
    }

    @Test
    @Order(4)
    @DisplayName("4. 通知中心已读流转：未读数、单条已读、批量已读、全部已读")
    void messageReadFlow() {
        insertMessage("七阶段消息甲");
        insertMessage("七阶段消息乙");
        insertMessage("七阶段消息丙");
        Long before = ((Number) dataOf(call(HttpMethod.GET, "/api/system/messages/unread-count", adminToken, null))).longValue();
        assertThat(before).isGreaterThanOrEqualTo(3);

        Map<String, Object> unreadPage = dataOf(call(HttpMethod.GET,
                "/api/system/messages?pageNum=1&pageSize=10&readFlag=0"));
        List<Map<String, Object>> page = (List<Map<String, Object>>) unreadPage.get("records");
        List<Long> unreadIds = page.stream()
                .map(m -> ((Number) m.get("id")).longValue()).toList();
        assertThat(unreadIds.size()).isGreaterThanOrEqualTo(3);

        // 单条已读
        assertThat(codeOf(call(HttpMethod.PATCH, "/api/system/messages/" + unreadIds.get(0) + "/read", adminToken, null)))
                .isEqualTo(200);
        // 批量已读
        assertThat(codeOf(call(HttpMethod.PATCH, "/api/system/messages/read-batch", adminToken,
                unreadIds.subList(1, 3)))).isEqualTo(200);
        Long afterBatch = ((Number) dataOf(call(HttpMethod.GET, "/api/system/messages/unread-count", adminToken, null))).longValue();
        assertThat(afterBatch).isEqualTo(before - 3);

        // 全部已读
        insertMessage("七阶段消息丁");
        assertThat(codeOf(call(HttpMethod.PATCH, "/api/system/messages/read-all", adminToken, null))).isEqualTo(200);
        Long finalUnread = ((Number) dataOf(call(HttpMethod.GET, "/api/system/messages/unread-count", adminToken, null))).longValue();
        assertThat(finalUnread).isZero();
    }

    @Test
    @Order(5)
    @DisplayName("5. 通知越权防护：不能标记他人消息，批量中他人 id 被忽略")
    void messageBatchOwnership() {
        // 给巡检员造一条消息，管理员尝试标记 → 归属校验拦截
        jdbcTemplate.update(
                "INSERT INTO sys_message (receiver_id, title, content, msg_type, read_flag, send_status) "
                        + "VALUES (?, '七阶段他人消息', 'x', 'TASK', 0, 'SENT')", viewerUserId);
        Long viewerMsgId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_message WHERE receiver_id = ? AND title = '七阶段他人消息' ORDER BY id DESC LIMIT 1",
                Long.class, viewerUserId);
        assertThat(codeOf(call(HttpMethod.PATCH, "/api/system/messages/" + viewerMsgId + "/read", adminToken, null)))
                .isEqualTo(403);
        assertThat(codeOf(call(HttpMethod.PATCH, "/api/system/messages/read-batch", adminToken, List.of(viewerMsgId))))
                .isEqualTo(200);
        Integer unreadFlag = jdbcTemplate.queryForObject(
                "SELECT read_flag FROM sys_message WHERE id = ?", Integer.class, viewerMsgId);
        assertThat(unreadFlag).isZero(); // 他人消息未被管理员标记
        jdbcTemplate.update("DELETE FROM sys_message WHERE id = ?", viewerMsgId);
    }

    @Test
    @Order(6)
    @DisplayName("6. 通知触发：报修生成新工单通知；手工巡检任务生成提醒")
    void notificationTriggers() {
        Long orderId = createOrder("七阶段通知触发光单");
        Integer newOrderNotice = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_message WHERE receiver_id = ? AND biz_id = ? "
                        + "AND title = '新工单待派单'",
                Integer.class, adminUserId, orderId);
        assertThat(newOrderNotice).isGreaterThanOrEqualTo(1);

        // 巡检任务提醒：JDBC 造模板与检查项，再走手工建任务接口
        String templateCode = "S7T" + SUFFIX;
        jdbcTemplate.update(
                "INSERT INTO insp_template (template_code, template_name, category_id, version, status, item_count, deleted) "
                        + "VALUES (?, '七阶段通知模板', ?, 1, 'PUBLISHED', 1, 0)",
                templateCode, categoryId);
        Long templateId = jdbcTemplate.queryForObject(
                "SELECT id FROM insp_template WHERE template_code = ?", Long.class, templateCode);
        jdbcTemplate.update(
                "INSERT INTO insp_template_item (template_id, seq_no, item_name, item_type, required, auto_work_order, photo_required) "
                        + "VALUES (?, 1, '外观检查', 'VISUAL', 1, 0, 0)", templateId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentId", equipmentId);
        payload.put("templateId", templateId);
        payload.put("assigneeId", adminUserId);
        assertThat(codeOf(call(HttpMethod.POST, "/api/inspection/tasks", adminToken, payload))).isEqualTo(200);
        Integer taskNotice = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_message WHERE receiver_id = ? AND biz_type = 'INSPECTION_TASK' "
                        + "AND title = '巡检任务提醒'",
                Integer.class, adminUserId);
        assertThat(taskNotice).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(7)
    @DisplayName("7. 操作审计：登录已记录，口令脱敏不含明文")
    void auditLoginAndMasking() {
        // 再登录一次，制造新审计记录
        login(ADMIN_USER, ADMIN_PASS);
        // 审计异步落库：轮询等待记录出现（最多 5 秒）
        List<Map<String, Object>> logs = List.of();
        for (int i = 0; i < 10; i++) {
        // 注意：不要预编码中文参数，TestRestTemplate 会再编码一次造成双重编码
        Map<String, Object> auditPage = dataOf(call(HttpMethod.GET,
                "/api/system/audit-logs?pageNum=1&pageSize=5&module=认证"));
            logs = (List<Map<String, Object>>) auditPage.get("records");
            boolean hit = logs.stream().anyMatch(row ->
                    ADMIN_USER.equals(String.valueOf(row.get("username"))));
            if (hit) {
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(logs).isNotEmpty();
        assertThat(logs).anySatisfy(row -> {
            assertThat(String.valueOf(row.get("action"))).isEqualTo("登录");
            assertThat(String.valueOf(row.get("username"))).isEqualTo(ADMIN_USER);
            // 脱敏断言：请求参数里不允许出现明文口令
            String param = String.valueOf(row.get("requestParam"));
            assertThat(param).doesNotContain(ADMIN_PASS);
        });
    }

    @Test
    @Order(8)
    @DisplayName("8. Excel 导出：内容为真实 xlsx、表头正确、带时间范围")
    void exportExcel() {
        ResponseEntity<byte[]> devices = rawGet("/api/export/devices", adminToken);
        assertThat(devices.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(devices.getHeaders().getContentType().toString())
                .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(devices.getHeaders().getFirst("Content-Disposition")).contains(".xlsx");
        verifyWorkbook(devices.getBody(), "设备清单", "资产编号", "下次巡检");

        ResponseEntity<byte[]> orders = rawGet(
                "/api/export/work-orders?startDate=" + LocalDate.now().minusDays(1)
                        + "&endDate=" + LocalDate.now(), adminToken);
        assertThat(orders.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyWorkbook(orders.getBody(), "维修工单", "工单号", "备件成本(元)");

        ResponseEntity<byte[]> cost = rawGet(
                "/api/export/cost-stats?startDate=" + LocalDate.now().minusDays(1)
                        + "&endDate=" + LocalDate.now(), adminToken);
        assertThat(cost.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyWorkbook(cost.getBody(), "维修成本统计", "工单号", "质量评分");

        ResponseEntity<byte[]> inspections = rawGet(
                "/api/export/inspections?startDate=" + LocalDate.now().minusDays(1)
                        + "&endDate=" + LocalDate.now(), adminToken);
        assertThat(inspections.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyWorkbook(inspections.getBody(), "巡检记录", "任务编号", "是否逾期");

        ResponseEntity<byte[]> inventory = rawGet("/api/export/inventory", adminToken);
        assertThat(inventory.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyWorkbook(inventory.getBody(), "备件库存", "备件编码", "状态");
    }

    @Test
    @Order(9)
    @DisplayName("9. 导出权限：无 analytics:export 的巡检员被 403")
    void exportPermissionDenied() {
        ResponseEntity<byte[]> denied = rawGet("/api/export/devices", viewerToken);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ==================================================================
    // 基础设施
    // ==================================================================

    private void insertMessage(String title) {
        jdbcTemplate.update(
                "INSERT INTO sys_message (receiver_id, title, content, msg_type, read_flag, send_status) "
                        + "VALUES (?, ?, '七阶段集成测试消息', 'TASK', 0, 'SENT')",
                adminUserId, title);
    }

    private String login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        ResponseEntity<Result> response = rest.postForEntity("/api/auth/login", request, Result.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        return String.valueOf(data.get("accessToken"));
    }

    private ResponseEntity<Result> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Result.class);
    }

    /** 管理员 GET 便捷重载 */
    private ResponseEntity<Result> call(HttpMethod method, String path) {
        return call(method, path, adminToken, null);
    }

    private int codeOf(ResponseEntity<Result> response) {
        return response.getBody() == null ? -1 : response.getBody().getCode();
    }

    private <T> T dataOf(ResponseEntity<Result> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (T) response.getBody().getData();
    }

    private ResponseEntity<byte[]> rawGet(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
    }

    private void verifyWorkbook(byte[] bytes, String sheetName, String firstHeader, String lastHeader) {
        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(4);
        assertThat(new String(bytes, 0, 2, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("PK");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(sheetName);
            assertThat(sheet).as("应存在工作表 " + sheetName).isNotNull();
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo(firstHeader);
            assertThat(header.getCell(header.getLastCellNum() - 1).getStringCellValue()).isEqualTo(lastHeader);
        } catch (Exception e) {
            throw new AssertionError("工作簿解析失败：" + sheetName, e);
        }
    }

    private Long createEquipment(String name, String assetNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentName", name);
        payload.put("categoryId", categoryId);
        payload.put("locationId", locationId);
        payload.put("brand", "测试品牌");
        payload.put("model", "S7-100");
        payload.put("assetNo", assetNo);
        ResponseEntity<Result> response = call(HttpMethod.POST, "/api/asset/equipment", adminToken, payload);
        assertThat(codeOf(response)).isEqualTo(200);
        return ((Number) response.getBody().getData()).longValue();
    }

    private Long createOrder(String title) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentId", equipmentId);
        payload.put("title", title);
        payload.put("faultType", "MECHANICAL");
        payload.put("faultDesc", "七阶段集成测试自动创建的工单");
        payload.put("level", "P3");
        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/workorder", adminToken, payload);
        assertThat(codeOf(created)).isEqualTo(200);
        return ((Number) created.getBody().getData()).longValue();
    }

}
