package com.equipcare;

import com.equipcare.common.result.Result;
import com.equipcare.inspection.service.InspectionGenerationService;
import com.equipcare.inspection.service.InspectionTaskService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第四阶段集成测试：智能巡检中心。
 *
 * <p>覆盖验收清单：
 * <ol>
 *   <li>每日 / 每周 / 每月三种周期的任务生成（用固定时刻驱动，不依赖真实时钟）；</li>
 *   <li>重复任务防护：同一计划同一设备同一天只允许一条，重复生成返回 0；</li>
 *   <li>服务重启后的补生成：模拟停机三天，下一轮把漏掉的日期补齐且不重复；</li>
 *   <li>暂停计划不生成、过期任务被标记；</li>
 *   <li>巡检提交三态判定：必检项不可跳过、异常必须填原因、需留证项必须有照片；</li>
 *   <li>异常转维修工单：只落关联，工单详情实时联查巡检来源与现场照片；</li>
 *   <li>不同角色的接口权限：模板/计划/统计均需对应权限码，后端直接 403。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
class Stage4IT {

    private static final String ADMIN_USERNAME = "stage4_admin";
    private static final String ADMIN_PASSWORD = "Stage4Admin123";
    private static final String INSPECTOR_USERNAME = "stage4_inspector";
    private static final String INSPECTOR_PASSWORD = "Stage4Insp123";
    private static final String VIEWER_USERNAME = "stage4_viewer";
    private static final String VIEWER_PASSWORD = "Stage4View123";

    private static final String TEMPLATE_CODE_PREFIX = "T4TPL";
    private static final String PLAN_CODE_PREFIX = "T4PLAN";

    /** 生成窗口的固定时刻：2026-03-10 是周二 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 10, 0, 30);
    /** 模拟停机三天后的时刻 */
    private static final LocalDateTime AFTER_RESTART = LocalDateTime.of(2026, 3, 16, 0, 30);

    private static final String[] ADMIN_PERMISSIONS = {
            "asset:equipment:view", "asset:equipment:create", "asset:equipment:update",
            "inspect:template:view", "inspect:template:manage",
            "inspect:plan:view", "inspect:plan:manage", "inspect:plan:generate",
            "inspect:task:view", "inspect:task:execute", "inspect:task:cancel",
            "inspect:anomaly:view", "inspect:anomaly:report", "inspect:anomaly:convert",
            "inspect:anomaly:close", "inspect:calendar:view", "inspect:stats:view",
            "wo:workorder:view", "system:message:view", "health:view"
    };

    private static final String[] INSPECTOR_PERMISSIONS = {
            "asset:equipment:view", "inspect:task:view", "inspect:task:execute",
            "inspect:anomaly:view", "inspect:anomaly:report", "inspect:calendar:view",
            "system:message:view"
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InspectionGenerationService generationService;

    @Autowired
    private InspectionTaskService taskService;

    private String adminToken;
    private String inspectorToken;
    private String viewerToken;
    private Long adminUserId;
    private Long inspectorUserId;
    private Long viewerUserId;
    private Long categoryId;
    private Long locationId;
    private Long equipmentA;
    private Long equipmentB;
    private Long templateId;
    private Long dailyPlanId;
    private Long weeklyPlanId;
    private Long monthlyPlanId;
    private Long manualTaskId;
    private Long anomalyId;
    private Long workOrderId;
    private Long photoFileId;

    /* ------------------------------------------------------------------ */
    /* 准备与清理                                                          */
    /* ------------------------------------------------------------------ */

    @BeforeAll
    void prepare() throws Exception {
        cleanup();

        Long adminRoleId = ensureRole("STAGE4_ADMIN", "四阶段管理员", "ALL", 0);
        Long inspectorRoleId = ensureRole("STAGE4_INSPECTOR", "四阶段巡检员", "SELF", 0);
        Long viewerRoleId = ensureRole("STAGE4_VIEWER", "四阶段只读", "SELF", 0);

        for (String code : ADMIN_PERMISSIONS) {
            grant(adminRoleId, ensurePermission(code));
        }
        for (String code : INSPECTOR_PERMISSIONS) {
            grant(inspectorRoleId, ensurePermission(code));
        }
        grant(viewerRoleId, ensurePermission("inspect:task:view"));

        adminUserId = createUser(ADMIN_USERNAME, ADMIN_PASSWORD, "四阶段管理员", adminRoleId);
        inspectorUserId = createUser(INSPECTOR_USERNAME, INSPECTOR_PASSWORD, "四阶段巡检员", inspectorRoleId);
        viewerUserId = createUser(VIEWER_USERNAME, VIEWER_PASSWORD, "四阶段只读用户", viewerRoleId);

        String ts = String.valueOf(System.currentTimeMillis());
        String suffix = ts.substring(ts.length() - 6);
        jdbcTemplate.update("INSERT INTO eq_category (parent_id, category_code, category_name, sort, status) "
                + "VALUES (0, ?, '四阶段测试类别', 91, 1)", "T4C" + suffix);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_category WHERE category_code = ?", Long.class, "T4C" + suffix);
        jdbcTemplate.update("INSERT INTO eq_location (parent_id, location_code, location_name, location_type, "
                + "full_path, sort, status) VALUES (0, ?, '四阶段测试位置', 'ROOM', '测试园区/四阶段位置', 91, 1)",
                "T4LOC" + suffix);
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_location WHERE location_code = ?", Long.class, "T4LOC" + suffix);

        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        inspectorToken = login(INSPECTOR_USERNAME, INSPECTOR_PASSWORD);
        viewerToken = login(VIEWER_USERNAME, VIEWER_PASSWORD);

        equipmentA = createEquipment("四阶段测试水泵A", "T4-" + suffix + "-A");
        equipmentB = createEquipment("四阶段测试水泵B", "T4-" + suffix + "-B");
    }

    @AfterAll
    void cleanupAll() {
        cleanup();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_message WHERE receiver_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'stage4\\_%')");
        // wo_status_log 有 append-only 触发器、且以外键引用工单，工单与其流转留痕
        // 都按业务规则不可删除，这里保留；设备改用逻辑删除，避免撞外键。
        jdbcTemplate.update("DELETE FROM insp_anomaly WHERE equipment_id IN "
                + "(SELECT id FROM eq_equipment WHERE asset_no LIKE 'T4-%')");
        jdbcTemplate.update("DELETE FROM insp_task_item WHERE task_id IN "
                + "(SELECT id FROM insp_task WHERE equipment_id IN "
                + "(SELECT id FROM eq_equipment WHERE asset_no LIKE 'T4-%'))");
        jdbcTemplate.update("DELETE FROM insp_task WHERE equipment_id IN "
                + "(SELECT id FROM eq_equipment WHERE asset_no LIKE 'T4-%')");
        jdbcTemplate.update("DELETE FROM insp_plan_equipment WHERE plan_id IN "
                + "(SELECT id FROM insp_plan WHERE plan_code LIKE 'T4PLAN%')");
        jdbcTemplate.update("DELETE FROM insp_plan WHERE plan_code LIKE 'T4PLAN%'");
        jdbcTemplate.update("DELETE FROM insp_template_item WHERE template_id IN "
                + "(SELECT id FROM insp_template WHERE template_code LIKE 'T4TPL%')");
        jdbcTemplate.update("DELETE FROM insp_template WHERE template_code LIKE 'T4TPL%'");
        jdbcTemplate.update("UPDATE eq_equipment SET deleted = 1 WHERE asset_no LIKE 'T4-%'");
        // 设备行保留（逻辑删除），其引用的分类/位置同样只能逻辑删除
        jdbcTemplate.update("UPDATE eq_category SET deleted = 1 WHERE category_code LIKE 'T4C%' "
                + "OR category_code LIKE 'T4EMPTY%'");
        jdbcTemplate.update("UPDATE eq_location SET deleted = 1 WHERE location_code LIKE 'T4LOC%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'stage4\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'stage4\\_%'");
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code LIKE 'STAGE4\\_%')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 'STAGE4\\_%'");
    }

    private Long ensureRole(String code, String name, String scope, int builtin) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_role WHERE role_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        if (id != null) {
            return id.longValue();
        }
        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, data_scope, builtin, sort, status) "
                + "VALUES (?, ?, ?, ?, 60, 1)", code, name, scope, builtin);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, code);
    }

    private Long ensurePermission(String code) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_permission WHERE perm_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        if (id != null) {
            return id.longValue();
        }
        jdbcTemplate.update("INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, sort, visible, status) "
                + "VALUES (0, ?, ?, 'API', 95, 1, 1)", code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_permission WHERE perm_code = ?", Long.class, code);
    }

    private void grant(Long roleId, Long permissionId) {
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);
    }

    private Long createUser(String username, String password, String realName, Long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user (username, password, real_name, status) VALUES (?, ?, ?, 1)",
                username, passwordEncoder.encode(password), realName);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        return userId;
    }

    /* ------------------------------------------------------------------ */
    /* 工具                                                                */
    /* ------------------------------------------------------------------ */

    private String login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        ResponseEntity<Result> response = restTemplate.postForEntity("/api/auth/login", request, Result.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
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

    private Map<String, Object> dataOf(ResponseEntity<Result> response) {
        assertThat(response.getBody()).isNotNull();
        return (Map<String, Object>) response.getBody().getData();
    }

    private Long createEquipment(String name, String assetNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentName", name);
        payload.put("categoryId", categoryId);
        payload.put("locationId", locationId);
        payload.put("brand", "测试品牌");
        payload.put("model", "T4-100");
        payload.put("assetNo", assetNo);
        ResponseEntity<Result> response = call(HttpMethod.POST, "/api/asset/equipment", adminToken, payload);
        assertThat(codeOf(response)).isEqualTo(200);
        return ((Number) response.getBody().getData()).longValue();
    }

    private Map<String, Object> item(String name, String type, Map<String, Object> extra) {
        Map<String, Object> item = new HashMap<>();
        item.put("itemName", name);
        item.put("itemType", type);
        item.put("required", extra.getOrDefault("required", 1));
        item.put("abnormalLevel", extra.getOrDefault("abnormalLevel", "MEDIUM"));
        item.put("autoWorkOrder", extra.getOrDefault("autoWorkOrder", 0));
        item.put("photoRequired", extra.getOrDefault("photoRequired", 0));
        extra.forEach((key, value) -> {
            if (!"required".equals(key) && !"abnormalLevel".equals(key)) {
                item.put(key, value);
            }
        });
        return item;
    }

    private Map<String, Object> planPayload(String name, String code, Long template, String cycleType,
                                            String time, List<Integer> weekDays, List<Integer> monthDays,
                                            String startDate, String endDate, int aheadHours) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("planName", name);
        payload.put("planCode", code);
        payload.put("templateId", template);
        payload.put("scopeType", "CATEGORY");
        payload.put("scopeIds", List.of(categoryId));
        payload.put("executorStrategy", "FIXED");
        payload.put("executorIds", List.of(inspectorUserId));
        payload.put("cycleType", cycleType);
        payload.put("executeTime", time);
        if (weekDays != null) {
            payload.put("weekDays", weekDays);
        }
        if (monthDays != null) {
            payload.put("monthDays", monthDays);
        }
        payload.put("startDate", startDate);
        if (endDate != null) {
            payload.put("endDate", endDate);
        }
        payload.put("slaMinutes", 480);
        payload.put("generateAheadHours", aheadHours);
        payload.put("status", "ENABLED");
        return payload;
    }

    private Long uploadPhoto(String token) throws Exception {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] png = out.toByteArray();

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "inspection.png";
            }
        });
        form.add("bizType", "INSPECT");
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Result> upload = restTemplate.postForEntity(
                "/api/files/upload", new HttpEntity<>(form, headers), Result.class);
        assertThat(codeOf(upload)).isEqualTo(200);
        return ((Number) dataOf(upload).get("fileId")).longValue();
    }

    private int countTasks(Long planId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM insp_task WHERE plan_id = ?", Integer.class, planId);
        return count == null ? 0 : count;
    }

    /* ------------------------------------------------------------------ */
    /* 用例                                                                */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(1)
    @DisplayName("1. 模板管理：检查项可判定性校验、发布后才能被计划引用")
    void templateLifecycle() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("templateName", "四阶段水泵巡检模板");
        payload.put("templateCode", TEMPLATE_CODE_PREFIX + System.currentTimeMillis() % 1000000);
        payload.put("categoryId", categoryId);
        payload.put("description", "覆盖运行声音、外壳温度、管道渗漏");
        payload.put("items", List.of(
                item("检查设备运行声音", "ENUM", Map.of(
                        "enumOptions", "[\"正常\",\"异响\",\"噪声过大\"]",
                        "normalValue", "正常",
                        "checkMethod", "听诊判断",
                        "standardRef", "设备操作规程 5.1",
                        "abnormalRule", "出现异响即判为异常",
                        "autoWorkOrder", 1,
                        "abnormalLevel", "HIGH")),
                item("检查设备外壳温度", "NUMBER", Map.of(
                        "unit", "℃", "minValue", 0, "maxValue", 60,
                        "photoRequired", 1,
                        "abnormalRule", "超出 0~60℃ 判为异常并附测温照片",
                        "abnormalLevel", "MEDIUM")),
                item("检查管道是否漏水", "BOOLEAN", Map.of(
                        "normalValue", "0",
                        "abnormalLevel", "HIGH"))));

        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/inspection/templates", adminToken, payload);
        assertThat(codeOf(created)).isEqualTo(200);
        templateId = ((Number) created.getBody().getData()).longValue();

        // 数值项没给区间：存进去也无法判定，直接拒绝
        Map<String, Object> badPayload = new HashMap<>(payload);
        badPayload.put("templateCode", null);
        badPayload.put("items", List.of(item("无区间的温度项", "NUMBER", Map.of("unit", "℃"))));
        ResponseEntity<Result> bad = call(HttpMethod.POST, "/api/inspection/templates", adminToken, badPayload);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(codeOf(bad)).isEqualTo(400);

        ResponseEntity<Result> publish = call(HttpMethod.POST,
                "/api/inspection/templates/" + templateId + "/publish", adminToken, null);
        assertThat(codeOf(publish)).isEqualTo(200);

        Map<String, Object> detail = dataOf(call(HttpMethod.GET,
                "/api/inspection/templates/" + templateId, adminToken, null));
        assertThat(detail.get("status")).isEqualTo("PUBLISHED");
        assertThat((List<?>) detail.get("items")).hasSize(3);
        assertThat(((Number) detail.get("itemCount")).intValue()).isEqualTo(3);

        // 编辑已发布模板：版本号 +1，检查项整组替换
        Map<String, Object> updatePayload = new HashMap<>(payload);
        updatePayload.put("templateCode", null);
        updatePayload.put("templateName", "四阶段水泵巡检模板（改）");
        updatePayload.put("items", List.of(
                item("检查设备运行声音", "ENUM", Map.of(
                        "enumOptions", "[\"正常\",\"异响\"]",
                        "normalValue", "正常",
                        "abnormalLevel", "HIGH")),
                item("检查设备外壳温度", "NUMBER", Map.of(
                        "unit", "℃", "minValue", 0, "maxValue", 65,
                        "photoRequired", 1,
                        "abnormalLevel", "MEDIUM")),
                item("检查管道是否漏水", "BOOLEAN", Map.of("normalValue", "0", "abnormalLevel", "HIGH"))));
        assertThat(codeOf(call(HttpMethod.PUT, "/api/inspection/templates/" + templateId,
                adminToken, updatePayload))).isEqualTo(200);
        Map<String, Object> afterUpdate = dataOf(call(HttpMethod.GET,
                "/api/inspection/templates/" + templateId, adminToken, null));
        assertThat(((Number) afterUpdate.get("version")).intValue()).isEqualTo(2);
    }

    @Test
    @Order(2)
    @DisplayName("2. 计划管理：作用域展开为设备快照，范围内无设备时拒绝保存")
    void planLifecycle() {
        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/inspection/plans", adminToken,
                planPayload("四阶段每日巡检", PLAN_CODE_PREFIX + "DAILY",
                        templateId, "DAILY", "08:00", null, null, "2026-03-10", null, 72));
        assertThat(codeOf(created)).isEqualTo(200);
        dailyPlanId = ((Number) created.getBody().getData()).longValue();

        Map<String, Object> detail = dataOf(call(HttpMethod.GET,
                "/api/inspection/plans/" + dailyPlanId, adminToken, null));
        assertThat(((Number) detail.get("equipmentCount")).intValue()).isEqualTo(2);
        assertThat(detail.get("status")).isEqualTo("ENABLED");
        assertThat(detail.get("cycleLabel")).asString().contains("每日");
        assertThat(detail.get("executorNames")).asString().contains("四阶段巡检员");
        assertThat(detail.get("nextGenerateTime")).isNotNull();

        // 分类下没有设备时不允许保存空计划
        String emptyCode = "T4EMPTY" + System.currentTimeMillis() % 100000;
        jdbcTemplate.update("INSERT INTO eq_category (parent_id, category_code, category_name, sort, status) "
                + "VALUES (0, ?, '四阶段空类别', 92, 1)", emptyCode);
        Long emptyCategoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_category WHERE category_code = ?", Long.class, emptyCode);
        Map<String, Object> emptyPlan = planPayload("四阶段空范围计划", PLAN_CODE_PREFIX + "EMPTY",
                templateId, "DAILY", "08:00", null, null, "2026-03-10", null, 24);
        emptyPlan.put("scopeIds", List.of(emptyCategoryId));
        ResponseEntity<Result> empty = call(HttpMethod.POST, "/api/inspection/plans", adminToken, emptyPlan);
        assertThat(empty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(empty.getBody().getMessage()).contains("没有可巡检设备");
    }

    @Test
    @Order(3)
    @DisplayName("3. 每日/每周/每月任务生成：按周期与提前窗口精确生成")
    void generateByCycle() {
        Map<String, Object> weekly = planPayload("四阶段每周巡检", PLAN_CODE_PREFIX + "WEEKLY",
                templateId, "WEEKLY", "08:00", List.of(2, 4), null, "2026-03-10", null, 72);
        weeklyPlanId = ((Number) call(HttpMethod.POST, "/api/inspection/plans", adminToken, weekly)
                .getBody().getData()).longValue();

        Map<String, Object> monthly = planPayload("四阶段每月巡检", PLAN_CODE_PREFIX + "MONTHLY",
                templateId, "MONTHLY", "08:00", null, List.of(11, 20, 31), "2026-03-10", null, 72);
        monthlyPlanId = ((Number) call(HttpMethod.POST, "/api/inspection/plans", adminToken, monthly)
                .getBody().getData()).longValue();

        // 每日：03-10 / 11 / 12 三天，每天 2 台设备（窗口截止 03-13 00:30，13 日 08:00 尚未进入）
        InspectionGenerationService.Summary daily = generationService.generateForPlan(dailyPlanId, NOW, false);
        assertThat(daily.created()).isEqualTo(6);
        assertThat(countTasks(dailyPlanId)).isEqualTo(6);

        // 每周：周二 03-10、周四 03-12，各 2 台
        InspectionGenerationService.Summary weeklySummary =
                generationService.generateForPlan(weeklyPlanId, NOW, false);
        assertThat(weeklySummary.created()).isEqualTo(4);
        assertThat(countTasks(weeklyPlanId)).isEqualTo(4);

        // 每月：03-11 命中，2 台
        InspectionGenerationService.Summary monthlySummary =
                generationService.generateForPlan(monthlyPlanId, NOW, false);
        assertThat(monthlySummary.created()).isEqualTo(2);
        assertThat(countTasks(monthlyPlanId)).isEqualTo(2);

        // 生成出来的任务必须带计划、执行人、SLA 与 AUTO 来源
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, source, assignee_id, sla_due_time FROM insp_task WHERE plan_id = ? ORDER BY id LIMIT 1",
                dailyPlanId);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("source")).isEqualTo("AUTO");
        assertThat(((Number) row.get("assignee_id")).longValue()).isEqualTo(inspectorUserId);
        assertThat(row.get("sla_due_time")).isNotNull();
    }

    @Test
    @Order(4)
    @DisplayName("4. 重复生成防护与重启回补：唯一索引兜底，停机三天自动补齐且不重复")
    void duplicateProtectionAndRestartRecovery() {
        InspectionGenerationService.Summary again = generationService.generateForPlan(dailyPlanId, NOW, false);
        assertThat(again.created()).isZero();
        assertThat(again.duplicate()).isEqualTo(6);
        assertThat(countTasks(dailyPlanId)).isEqualTo(6);

        // 数据库层面不允许同一计划同一设备同一天出现两条
        Integer duplicates = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM (SELECT plan_id, equipment_id, plan_date FROM insp_task "
                        + "WHERE plan_id = ? GROUP BY plan_id, equipment_id, plan_date HAVING COUNT(1) > 1) t",
                Integer.class, dailyPlanId);
        assertThat(duplicates).isZero();

        // 模拟停机 6 天后重启（03-10 → 03-16）：回补窗口
        // [03-09 00:30, 03-19 00:30) 覆盖计划起始日 03-10 至 03-18 共 9 天，
        // 其中 03-10~03-12 已生成，需补 03-13~03-18 共 6 天 × 2 台 = 12 条
        InspectionGenerationService.Summary recovered =
                generationService.generateForPlan(dailyPlanId, AFTER_RESTART, false);
        assertThat(recovered.created()).isEqualTo(12);
        assertThat(recovered.duplicate()).isEqualTo(6);
        assertThat(countTasks(dailyPlanId)).isEqualTo(18);
    }

    @Test
    @Order(5)
    @DisplayName("5. 暂停计划不再生成；过期任务被标记 EXPIRED")
    void pausedPlanAndExpiry() {
        assertThat(codeOf(call(HttpMethod.PATCH,
                "/api/inspection/plans/" + monthlyPlanId + "/status?status=DISABLED", adminToken, null)))
                .isEqualTo(200);

        InspectionGenerationService.Summary due = generationService.generateDue(LocalDateTime.of(2026, 3, 21, 0, 30));
        assertThat(due.plans()).isGreaterThanOrEqualTo(1);
        // 暂停的计划不应再加任务（否则 03-20 会命中）
        assertThat(countTasks(monthlyPlanId)).isEqualTo(2);

        ResponseEntity<Result> generatePaused = call(HttpMethod.POST,
                "/api/inspection/plans/" + monthlyPlanId + "/generate", adminToken, null);
        assertThat(generatePaused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(generatePaused)).isEqualTo(2004);

        // 过期：把一条待执行任务的时限改到过去
        Long pendingTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM insp_task WHERE plan_id = ? AND status = 'PENDING' ORDER BY id LIMIT 1",
                Long.class, dailyPlanId);
        jdbcTemplate.update("UPDATE insp_task SET sla_due_time = DATE_SUB(NOW(), INTERVAL 2 HOUR) WHERE id = ?",
                pendingTaskId);
        int affected = taskService.expireOverdue();
        assertThat(affected).isGreaterThanOrEqualTo(1);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM insp_task WHERE id = ?", String.class, pendingTaskId);
        assertThat(status).isEqualTo("EXPIRED");
    }

    @Test
    @Order(6)
    @DisplayName("6. 巡检提交：必检项不可跳过、异常须填原因、需留证项须有照片")
    void submitWithThreeStates() throws Exception {
        Map<String, Object> create = Map.of("equipmentId", equipmentA, "templateId", templateId,
                "assigneeId", inspectorUserId, "remark", "四阶段提交测试");
        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/inspection/tasks", adminToken, create);
        assertThat(codeOf(created)).isEqualTo(200);
        manualTaskId = ((Number) created.getBody().getData()).longValue();

        assertThat(codeOf(call(HttpMethod.POST, "/api/inspection/tasks/" + manualTaskId + "/start",
                inspectorToken, Map.of()))).isEqualTo(200);

        Map<String, Object> taskDetail = dataOf(call(HttpMethod.GET,
                "/api/inspection/tasks/" + manualTaskId, inspectorToken, null));
        List<Map<String, Object>> items = (List<Map<String, Object>>) taskDetail.get("items");
        assertThat(items).hasSize(3);
        Long soundItem = ((Number) items.get(0).get("templateItemId")).longValue();
        Long tempItem = ((Number) items.get(1).get("templateItemId")).longValue();
        Long leakItem = ((Number) items.get(2).get("templateItemId")).longValue();

        // 必检项跳过 → 400
        Map<String, Object> skipRequired = Map.of("items", List.of(
                Map.of("templateItemId", soundItem, "result", "SKIPPED", "skipReason", "设备停机"),
                Map.of("templateItemId", tempItem, "itemValue", "30", "result", "NORMAL"),
                Map.of("templateItemId", leakItem, "result", "NORMAL", "itemValue", "0")));
        ResponseEntity<Result> skipResponse = call(HttpMethod.POST,
                "/api/inspection/tasks/" + manualTaskId + "/submit", inspectorToken, skipRequired);
        assertThat(skipResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(skipResponse.getBody().getMessage()).contains("不能跳过");

        // 异常未填原因 → 400
        Map<String, Object> noReason = Map.of("items", List.of(
                Map.of("templateItemId", soundItem, "result", "ABNORMAL", "itemValue", "异响"),
                Map.of("templateItemId", tempItem, "itemValue", "30", "result", "NORMAL"),
                Map.of("templateItemId", leakItem, "result", "NORMAL", "itemValue", "0")));
        ResponseEntity<Result> noReasonResponse = call(HttpMethod.POST,
                "/api/inspection/tasks/" + manualTaskId + "/submit", inspectorToken, noReason);
        assertThat(noReasonResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noReasonResponse.getBody().getMessage()).contains("必须填写异常说明");

        // 需留证的异常项没有照片 → 400
        Map<String, Object> noPhoto = Map.of("items", List.of(
                Map.of("templateItemId", soundItem, "result", "NORMAL", "itemValue", "正常"),
                Map.of("templateItemId", tempItem, "itemValue", "95", "result", "ABNORMAL",
                        "remark", "外壳温度偏高"),
                Map.of("templateItemId", leakItem, "result", "NORMAL", "itemValue", "0")));
        ResponseEntity<Result> noPhotoResponse = call(HttpMethod.POST,
                "/api/inspection/tasks/" + manualTaskId + "/submit", inspectorToken, noPhoto);
        assertThat(noPhotoResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(noPhotoResponse.getBody().getMessage()).contains("现场照片");

        // 数值项标正常但实测值超区间 → 服务端拒绝，不接受自相矛盾的记录
        Map<String, Object> contradictory = Map.of("items", List.of(
                Map.of("templateItemId", soundItem, "result", "NORMAL", "itemValue", "正常"),
                Map.of("templateItemId", tempItem, "itemValue", "95", "result", "NORMAL"),
                Map.of("templateItemId", leakItem, "result", "NORMAL", "itemValue", "0")));
        ResponseEntity<Result> contradictoryResponse = call(HttpMethod.POST,
                "/api/inspection/tasks/" + manualTaskId + "/submit", inspectorToken, contradictory);
        assertThat(contradictoryResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(contradictoryResponse.getBody().getMessage()).contains("超出标准区间");

        // 结构化项（是否/枚举/数值）空值判正常 → 拒绝：空值不能作为"正常"的依据
        Map<String, Object> blankValue = Map.of("items", List.of(
                Map.of("templateItemId", soundItem, "result", "NORMAL", "itemValue", "正常"),
                Map.of("templateItemId", tempItem, "itemValue", "30", "result", "NORMAL"),
                Map.of("templateItemId", leakItem, "result", "NORMAL")));
        ResponseEntity<Result> blankValueResponse = call(HttpMethod.POST,
                "/api/inspection/tasks/" + manualTaskId + "/submit", inspectorToken, blankValue);
        assertThat(blankValueResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(blankValueResponse.getBody().getMessage()).contains("空值不能作为正常依据");

        // 合法提交：温度异常 + 现场照片，声音与渗漏正常
        photoFileId = uploadPhoto(inspectorToken);
        Map<String, Object> valid = Map.of("items", List.of(
                Map.of("templateItemId", soundItem, "result", "NORMAL", "itemValue", "正常"),
                Map.of("templateItemId", tempItem, "itemValue", "95", "result", "ABNORMAL",
                        "remark", "外壳温度达 95℃，疑似散热不良",
                        "attachmentIds", String.valueOf(photoFileId)),
                Map.of("templateItemId", leakItem, "result", "NORMAL", "itemValue", "0")),
                "remark", "四阶段合法提交");
        ResponseEntity<Result> submitted = call(HttpMethod.POST,
                "/api/inspection/tasks/" + manualTaskId + "/submit", inspectorToken, valid);
        assertThat(codeOf(submitted)).isEqualTo(200);
        assertThat(((Number) submitted.getBody().getData()).intValue()).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, result, abnormal_count FROM insp_task WHERE id = ?", manualTaskId);
        assertThat(row.get("status")).isEqualTo("SUBMITTED");
        assertThat(row.get("result")).isEqualTo("ABNORMAL");
        assertThat(((Number) row.get("abnormal_count")).intValue()).isEqualTo(1);

        List<Map<String, Object>> stored = jdbcTemplate.queryForList(
                "SELECT result, is_abnormal, skip_reason, attachment_ids FROM insp_task_item "
                        + "WHERE task_id = ? ORDER BY id", manualTaskId);
        assertThat(stored).hasSize(3);
        assertThat(stored.get(0).get("result")).isEqualTo("NORMAL");
        assertThat(stored.get(1).get("result")).isEqualTo("ABNORMAL");
        assertThat(((Number) stored.get(1).get("is_abnormal")).intValue()).isEqualTo(1);
        assertThat(stored.get(1).get("attachment_ids")).isEqualTo(String.valueOf(photoFileId));

        // 重复提交被拒
        ResponseEntity<Result> again = call(HttpMethod.POST,
                "/api/inspection/tasks/" + manualTaskId + "/submit", inspectorToken, valid);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(again)).isEqualTo(2004);

        Map<String, Object> anomalies = dataOf(call(HttpMethod.GET,
                "/api/inspection/anomalies?taskId=" + manualTaskId, adminToken, null));
        List<Map<String, Object>> records = (List<Map<String, Object>>) anomalies.get("records");
        assertThat(records).hasSize(1);
        anomalyId = ((Number) records.get(0).get("id")).longValue();
        assertThat((List<?>) records.get(0).get("attachments")).hasSize(1);
        assertThat(records.get(0).get("itemValue")).isEqualTo("95");
    }

    @Test
    @Order(7)
    @DisplayName("7. 异常转维修工单：只落关联，工单详情实时联查巡检来源与现场照片")
    void anomalyToWorkOrder() {
        ResponseEntity<Result> converted = call(HttpMethod.POST,
                "/api/inspection/anomalies/" + anomalyId + "/work-order", adminToken,
                Map.of("level", "P2", "remark", "请优先安排检修"));
        assertThat(codeOf(converted)).isEqualTo(200);
        workOrderId = ((Number) converted.getBody().getData()).longValue();
        assertThat(workOrderId).isPositive();

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT anomaly_id, source_id, source, level FROM wo_work_order WHERE id = ?", workOrderId);
        assertThat(((Number) order.get("anomaly_id")).longValue()).isEqualTo(anomalyId);
        assertThat(((Number) order.get("source_id")).longValue()).isEqualTo(manualTaskId);
        assertThat(order.get("source")).isEqualTo("INSPECT");
        assertThat(order.get("level")).isEqualTo("P2");

        Map<String, Object> anomalyRow = jdbcTemplate.queryForMap(
                "SELECT status, work_order_id FROM insp_anomaly WHERE id = ?", anomalyId);
        assertThat(anomalyRow.get("status")).isEqualTo("WORK_ORDER");
        assertThat(((Number) anomalyRow.get("work_order_id")).longValue()).isEqualTo(workOrderId);

        // 工单详情带出巡检来源（任务号、异常项、实测值、现场照片）
        Map<String, Object> detail = dataOf(call(HttpMethod.GET,
                "/api/workorder/" + workOrderId, adminToken, null));
        Map<String, Object> source = (Map<String, Object>) detail.get("sourceInspection");
        assertThat(source).isNotNull();
        assertThat(((Number) source.get("anomalyId")).longValue()).isEqualTo(anomalyId);
        assertThat(((Number) source.get("taskId")).longValue()).isEqualTo(manualTaskId);
        assertThat(source.get("taskCode")).asString().startsWith("IT");
        assertThat(source.get("itemName")).isEqualTo("检查设备外壳温度");
        assertThat(source.get("itemValue")).isEqualTo("95");
        assertThat((List<?>) source.get("attachments")).hasSize(1);

        // 重复转单被拒
        ResponseEntity<Result> again = call(HttpMethod.POST,
                "/api/inspection/anomalies/" + anomalyId + "/work-order", adminToken, Map.of());
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(again)).isEqualTo(2004);
    }

    @Test
    @Order(8)
    @DisplayName("8. 角色权限：巡检员不能建模板/计划，看不到统计；只读账号只能看任务")
    void permissionMatrix() {
        Map<String, Object> templatePayload = Map.of(
                "templateName", "四阶段越权模板",
                "items", List.of(item("越权项", "BOOLEAN", Map.of("normalValue", "1"))));
        ResponseEntity<Result> inspectorTemplate = call(HttpMethod.POST,
                "/api/inspection/templates", inspectorToken, templatePayload);
        assertThat(inspectorTemplate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> planPayload = planPayload("四阶段越权计划", null, templateId, "DAILY",
                "08:00", null, null, "2026-03-10", null, 24);
        ResponseEntity<Result> inspectorPlan = call(HttpMethod.POST,
                "/api/inspection/plans", inspectorToken, planPayload);
        assertThat(inspectorPlan.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 巡检员能看日历，但没有统计权限
        assertThat(codeOf(call(HttpMethod.GET, "/api/inspection/calendar?month=2026-03",
                inspectorToken, null))).isEqualTo(200);
        ResponseEntity<Result> inspectorStats = call(HttpMethod.GET,
                "/api/inspection/stats?days=30", inspectorToken, null);
        assertThat(inspectorStats.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 只读账号：能看任务列表，不能看统计，不能提交
        assertThat(codeOf(call(HttpMethod.GET, "/api/inspection/tasks?pageNum=1&pageSize=5",
                viewerToken, null))).isEqualTo(200);
        ResponseEntity<Result> viewerStats = call(HttpMethod.GET,
                "/api/inspection/stats?days=30", viewerToken, null);
        assertThat(viewerStats.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // 越权提交：请求体要能通过参数校验，否则会先被 422 拦下而测不到权限
        Long viewerItem = jdbcTemplate.queryForObject(
                "SELECT template_item_id FROM insp_task_item WHERE task_id = ? ORDER BY id LIMIT 1",
                Long.class, manualTaskId);
        Map<String, Object> viewerPayload = Map.of("items", List.of(
                Map.of("templateItemId", viewerItem, "result", "NORMAL", "itemValue", "正常")));
        ResponseEntity<Result> viewerSubmit = call(HttpMethod.POST,
                "/api/inspection/tasks/" + manualTaskId + "/submit", viewerToken, viewerPayload);
        assertThat(viewerSubmit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 未登录一律 401
        assertThat(call(HttpMethod.GET, "/api/inspection/plans?pageNum=1&pageSize=5", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 管理员统计可用，且完成率分母为正
        Map<String, Object> stats = dataOf(call(HttpMethod.GET,
                "/api/inspection/stats?days=365", adminToken, null));
        assertThat(((Number) stats.get("totalTasks")).intValue()).isGreaterThan(0);
        assertThat(((Number) stats.get("completionRate")).doubleValue()).isBetween(0d, 100d);
    }

    @Test
    @Order(9)
    @DisplayName("9. 巡检日历按计划日聚合，二维码接口按任务权限放行")
    void calendarAndTaskQr() {
        List<Map<String, Object>> calendar = (List<Map<String, Object>>) call(HttpMethod.GET,
                "/api/inspection/calendar?month=2026-03", adminToken, null).getBody().getData();
        assertThat(calendar).isNotEmpty();
        Map<String, Object> day = calendar.stream()
                .filter(row -> String.valueOf(row.get("date")).startsWith("2026-03-10"))
                .findFirst().orElseThrow();
        // 03-10 命中两个计划：每日 09:00 与每周（周二）08:00，各 2 台设备
        assertThat(((Number) day.get("total")).intValue()).isEqualTo(4);
        // 当天没有任何提交记录，全部落在待执行/已过期，不应出现已提交或异常
        assertThat(((Number) day.get("submitted")).intValue()).isZero();
        assertThat(((Number) day.get("abnormal")).intValue()).isZero();

        // 巡检员没有设备二维码权限，但能通过任务拿到该设备的二维码
        ResponseEntity<byte[]> png = restTemplate.exchange(
                "/api/inspection/tasks/" + manualTaskId + "/equipment-qrcode.png?size=240",
                HttpMethod.GET, new HttpEntity<>(authHeaders(inspectorToken)), byte[].class);
        assertThat(png.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(png.getBody()).isNotNull();
        assertThat(png.getBody().length).isGreaterThan(100);
        assertThat(png.getBody()[0] & 0xFF).isEqualTo(0x89);
        assertThat(png.getBody()[1]).isEqualTo((byte) 'P');
    }
}
