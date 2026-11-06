package com.equipcare;

import com.equipcare.common.result.Result;
import com.equipcare.security.JwtProperties;
import com.equipcare.security.JwtTokenProvider;
import com.equipcare.common.security.LoginUser;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 集成测试：令牌生命周期 / 备件退料并发 / 工单成本并发。
 *
 * <p>对应整改批次：
 * <ol>
 *   <li>批三 · Refresh Token 生命周期：登出吊销、一次性使用、禁用拦截、用户隔离、过期拒绝；</li>
 *   <li>批七 · 并发双退：同一领料记录并发退料恰好一成一败，库存 / 成本只变化一次；</li>
 *   <li>批八 · 成本并发：同一工单并发出库多笔备件，成本 = 各笔之和，无丢失。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
class Stage11IT {

    private static final String SUFFIX = String.valueOf(System.currentTimeMillis()).substring(7);
    private static final String ADMIN_USER = "stage11_admin";
    private static final String USER_A = "stage11_a";
    private static final String USER_B = "stage11_b";
    private static final String PASS = "S11#" + SUFFIX + "x!";
    private static final String REPAIR_PASS = "S11#" + SUFFIX + "r!";

    private static final String[] ADMIN_PERMISSIONS = {
            "asset:equipment:view", "asset:equipment:create", "asset:equipment:update",
            "asset:equipment:delete",
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

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserDetailsService userDetailsService;

    private String adminToken;
    private Long adminUserId;
    private Long userAId;
    private Long repairUserId;
    private Long categoryId;
    private Long locationId;
    private Long equipmentId;
    private Long partId;

    @BeforeAll
    void setUp() {
        cleanup();
        // 角色码必须为状态机认可的内置角色（ADMIN / REPAIRMAN），否则工单动作被拒
        Long adminRole = ensureRole("ADMIN", "十一阶段管理员");
        Long repairRole = ensureRole("REPAIRMAN", "十一阶段维修员");
        for (String code : ADMIN_PERMISSIONS) {
            grantPermission(adminRole, code);
        }
        for (String code : REPAIR_PERMISSIONS) {
            grantPermission(repairRole, code);
        }

        adminUserId = createUser(ADMIN_USER, PASS, "十一阶段管理员", adminRole);
        userAId = createUser(USER_A, PASS, "十一阶段用户A", adminRole);
        createUser(USER_B, PASS, "十一阶段用户B", adminRole);
        repairUserId = createUser("stage11_repair", REPAIR_PASS, "十一阶段维修员", repairRole);

        jdbcTemplate.update("INSERT INTO eq_category (parent_id, category_code, category_name, sort, status) "
                + "VALUES (0, ?, '十一阶段类别', 95, 1)", "T11C" + SUFFIX);
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_category WHERE category_code = ?", Long.class, "T11C" + SUFFIX);
        jdbcTemplate.update("INSERT INTO eq_location (parent_id, location_code, location_name, location_type, "
                + "full_path, sort, status) VALUES (0, ?, '十一阶段位置', 'ROOM', '测试园区/十一阶段', 95, 1)",
                "T11LOC" + SUFFIX);
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_location WHERE location_code = ?", Long.class, "T11LOC" + SUFFIX);

        adminToken = loginToken(ADMIN_USER, PASS);
        equipmentId = createEquipment("十一阶段测试泵", "T11-" + SUFFIX);
        partId = createPart("T11-BRG-" + SUFFIX, "十一阶段轴承", new BigDecimal("88.50"), 10);
    }

    @AfterAll
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sp_work_order_part WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十一阶段%')");
        jdbcTemplate.update("DELETE FROM wo_dispatch_log WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十一阶段%')");
        jdbcTemplate.update("DELETE FROM wo_repair_record WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十一阶段%')");
        jdbcTemplate.update("DELETE FROM sla_alert_record WHERE work_order_id IN "
                + "(SELECT id FROM wo_work_order WHERE title LIKE '十一阶段%')");
        // wo_work_order / wo_status_log 均因外键 RESTRICT 或 append-only 触发器不可物理删除，
        // 与 Stage6IT 清理策略一致：关联流水记录删除，工单主行按标题前缀保留
        jdbcTemplate.update("UPDATE sp_part SET deleted = 1 WHERE part_no LIKE 'T11-%'");
        jdbcTemplate.update("UPDATE eq_equipment SET deleted = 1 WHERE asset_no LIKE 'T11-%'");
        jdbcTemplate.update("UPDATE eq_category SET deleted = 1 WHERE category_code LIKE 'T11C%'");
        jdbcTemplate.update("UPDATE eq_location SET deleted = 1 WHERE location_code LIKE 'T11LOC%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username LIKE 'stage11\\_%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE 'stage11\\_%'");
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code LIKE 'ST11%')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 'ST11%'");
        jdbcTemplate.update("DELETE FROM sys_permission WHERE perm_code LIKE 'st11:%'");
    }

    /* ------------------------------------------------------------------ */
    /* 批三 · 令牌生命周期                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(1)
    @DisplayName("1. 正常登录与刷新：新令牌对可用，旧 refreshToken 重放被拒")
    void loginAndRefresh() {
        Map<String, Object> tokens = loginFull(USER_A, PASS);
        String refreshToken = (String) tokens.get("refreshToken");

        // 正常刷新：得到新令牌对
        ResponseEntity<Result> refreshed = call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", refreshToken));
        assertThat(codeOf(refreshed)).isEqualTo(200);
        String newRefresh = (String) ((Map<String, Object>) refreshed.getBody().getData()).get("refreshToken");
        assertThat(newRefresh).isNotEqualTo(refreshToken);

        // 旧 refreshToken 重放被拒（一次性使用）
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", refreshToken)))).isEqualTo(1006);

        // 新 refreshToken 仍可正常刷新
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", newRefresh)))).isEqualTo(200);
    }

    @Test
    @Order(2)
    @DisplayName("2. 登出吊销：登出后访问令牌立即失效， refreshToken 不能再刷新")
    void logoutRevokesRefreshToken() {
        Map<String, Object> tokens = loginFull(USER_A, PASS);
        String access = (String) tokens.get("accessToken");
        String refresh = (String) tokens.get("refreshToken");

        // 登出：携带 refreshToken
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/logout", access,
                Map.of("refreshToken", refresh)))).isEqualTo(200);

        // 旧访问令牌被拉黑（业务码 1004 令牌无效）
        assertThat(codeOf(call(HttpMethod.GET, "/api/auth/me", access, null))).isEqualTo(1004);
        // 旧 refreshToken 不能再刷新（即使客户端不回传也已全量吊销）
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", refresh)))).isEqualTo(1006);

        // 不带 refreshToken 的登出：按访问令牌主体全量吊销
        Map<String, Object> again = loginFull(USER_A, PASS);
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/logout",
                (String) again.get("accessToken"), null))).isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", again.get("refreshToken"))))).isEqualTo(1006);
    }

    @Test
    @Order(3)
    @DisplayName("3. 用户隔离：吊销 A 的令牌不影响 B 正常刷新")
    void tokenIsolation() {
        Map<String, Object> a = loginFull(USER_A, PASS);
        Map<String, Object> b = loginFull(USER_B, PASS);

        // A 登出 → A 的 refresh 全吊销
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/logout",
                (String) a.get("accessToken"), Map.of("refreshToken", a.get("refreshToken")))))
                .isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", a.get("refreshToken"))))).isEqualTo(1006);

        // B 不受影响
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", b.get("refreshToken"))))).isEqualTo(200);
    }

    @Test
    @Order(4)
    @DisplayName("4. 禁用用户：refreshToken 换取新身份被拒，且吊销其全部刷新令牌")
    void disabledUserCannotRefresh() {
        Map<String, Object> tokens = loginFull(USER_B, PASS);
        String refresh = (String) tokens.get("refreshToken");

        jdbcTemplate.update("UPDATE sys_user SET status = 0 WHERE username = ?", USER_B);
        try {
            assertThat(codeOf(call(HttpMethod.POST, "/api/auth/refresh", null,
                    Map.of("refreshToken", refresh)))).isEqualTo(1002);
        } finally {
            jdbcTemplate.update("UPDATE sys_user SET status = 1 WHERE username = ?", USER_B);
        }
        // 禁用触发吊销后，恢复状态也不能再用旧 refreshToken
        assertThat(codeOf(call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", refresh)))).isEqualTo(1006);
    }

    @Test
    @Order(5)
    @DisplayName("5. 过期 refreshToken：拒绝刷新并提示重新登录")
    void expiredRefreshRejected() {
        // 与上下文同密钥但立即过期的刷新令牌
        JwtProperties props = new JwtProperties();
        props.setSecret(jwtProperties.getSecret());
        props.setRefreshTokenMinutes(0);
        JwtTokenProvider provider = new JwtTokenProvider(props);
        // 手动触发 @PostConstruct 初始化（签名密钥在 init() 中构建）
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(provider, "init");
        LoginUser loginUser = (LoginUser) userDetailsService.loadUserByUsername(USER_A);
        String expired = provider.generateRefreshToken(loginUser).token();

        ResponseEntity<Result> resp = call(HttpMethod.POST, "/api/auth/refresh", null,
                Map.of("refreshToken", expired));
        int code = codeOf(resp);
        assertThat(code).isIn(1006, 401);
    }

    @Autowired
    private JwtProperties jwtProperties;

    /* ------------------------------------------------------------------ */
    /* 批七 · 并发退料                                                      */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(6)
    @DisplayName("6. 并发双退：同一领料记录并发退料恰好一成一败，库存与成本只变化一次")
    void concurrentDoubleReturnRejected() throws Exception {
        Long orderId = createOrderAndAssign("十一阶段: 并发双退");
        // 申请 4 个（库存 10，足够），审批出库 → 库存 6、成本 354
        Long applyId = createdId(call(HttpMethod.POST, "/api/inventory/applies", adminToken,
                Map.of("workOrderId", orderId, "partId", partId, "quantity", 4)));
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/issue",
                adminToken, null))).isEqualTo(200);
        assertThat(stockOf(partId)).isEqualTo(6);
        assertThat(spareCostOf(orderId).doubleValue()).isEqualTo(354.0);
        int returnTxnsBefore = returnTxnCount(partId);

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<String> failure = new AtomicReference<>();
        try {
            Callable<ResponseEntity<Result>> task = () -> {
                latch.await();
                return call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/return",
                        adminToken, null);
            };
            Future<ResponseEntity<Result>> f1 = pool.submit(task);
            Future<ResponseEntity<Result>> f2 = pool.submit(task);
            latch.countDown();

            int ok = 0;
            int rejected = 0;
            for (Future<ResponseEntity<Result>> future : List.of(f1, f2)) {
                ResponseEntity<Result> resp = future.get(30, TimeUnit.SECONDS);
                int code = codeOf(resp);
                if (code == 200) {
                    ok++;
                } else if (code == 2004) {
                    rejected++;
                } else {
                    failure.set("意外的返回码 " + code + "：" + resp.getBody().getMessage());
                }
            }
            assertThat(failure.get()).as("并发退料返回码").isNull();
            assertThat(ok).as("恰好一次成功").isEqualTo(1);
            assertThat(rejected).as("恰好一次被拒").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        // 库存只回增一次、成本只冲减一次、RETURN 流水只有一条
        assertThat(stockOf(partId)).as("库存回增一次").isEqualTo(10);
        assertThat(spareCostOf(orderId).doubleValue()).as("成本冲减一次").isEqualTo(0.0);
        assertThat(returnTxnCount(partId)).as("RETURN 流水一条").isEqualTo(returnTxnsBefore + 1);
    }

    @Test
    @Order(7)
    @DisplayName("7. 重复退料：第二次被拒（状态门禁），库存与成本保持一致")
    void repeatedReturnRejected() {
        Long orderId = createOrderAndAssign("十一阶段: 重复退料");
        Long applyId = createdId(call(HttpMethod.POST, "/api/inventory/applies", adminToken,
                Map.of("workOrderId", orderId, "partId", partId, "quantity", 2)));
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/issue",
                adminToken, null))).isEqualTo(200);
        assertThat(stockOf(partId)).isEqualTo(8);

        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/return",
                adminToken, null))).isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/inventory/applies/" + applyId + "/return",
                adminToken, null))).isEqualTo(2004);
        assertThat(stockOf(partId)).as("不重复回增").isEqualTo(10);
        assertThat(spareCostOf(orderId).doubleValue()).as("不重复冲减").isEqualTo(0.0);
    }

    /* ------------------------------------------------------------------ */
    /* 批八 · 工单成本并发                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(8)
    @DisplayName("8. 并发出库记账：同一工单并发审批两笔备件，成本 = 两笔之和")
    void concurrentIssueCostAccumulates() throws Exception {
        Long orderId = createOrderAndAssign("十一阶段: 并发成本累加");
        Long apply1 = createdId(call(HttpMethod.POST, "/api/inventory/applies", adminToken,
                Map.of("workOrderId", orderId, "partId", partId, "quantity", 1)));
        Long apply2 = createdId(call(HttpMethod.POST, "/api/inventory/applies", adminToken,
                Map.of("workOrderId", orderId, "partId", partId, "quantity", 2)));

        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<ResponseEntity<Result>> t1 = () -> {
                latch.await();
                return call(HttpMethod.POST, "/api/inventory/applies/" + apply1 + "/issue", adminToken, null);
            };
            Callable<ResponseEntity<Result>> t2 = () -> {
                latch.await();
                return call(HttpMethod.POST, "/api/inventory/applies/" + apply2 + "/issue", adminToken, null);
            };
            Future<ResponseEntity<Result>> f1 = pool.submit(t1);
            Future<ResponseEntity<Result>> f2 = pool.submit(t2);
            latch.countDown();
            assertThat(codeOf(f1.get(30, TimeUnit.SECONDS))).isEqualTo(200);
            assertThat(codeOf(f2.get(30, TimeUnit.SECONDS))).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        // 88.50×1 + 88.50×2 = 265.50，并发下无丢失
        assertThat(spareCostOf(orderId).doubleValue()).as("并发出库成本累计").isEqualTo(265.50);
        assertThat(stockOf(partId)).isEqualTo(7);
    }

    /* ------------------------------------------------------------------ */
    /* 基础设施                                                            */
    /* ------------------------------------------------------------------ */

    private int returnTxnCount(Long partId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sp_stock_txn WHERE part_id = ? AND txn_type = 'RETURN'",
                Integer.class, partId);
        return count == null ? 0 : count;
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

    private Long createOrderAndAssign(String title) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("equipmentId", equipmentId);
        payload.put("title", title);
        payload.put("faultType", "MECHANICAL");
        payload.put("faultDesc", "十一阶段集成测试自动创建的工单");
        payload.put("level", "P3");
        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/workorder", adminToken, payload);
        assertThat(codeOf(created)).isEqualTo(200);
        Long orderId = ((Number) created.getBody().getData()).longValue();
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/dispatch", adminToken,
                Map.of("assigneeId", repairUserId, "reason", "测试派单")))).isEqualTo(200);
        assertThat(codeOf(call(HttpMethod.POST, "/api/workorder/" + orderId + "/accept",
                loginToken("stage11_repair", REPAIR_PASS), null))).isEqualTo(200);
        return orderId;
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

    private Long ensureRole(String code, String name) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_role WHERE role_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        if (id != null) {
            return id.longValue();
        }
        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, data_scope, builtin, sort, status) "
                + "VALUES (?, ?, 'ALL', 0, 96, 1)", code, name);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, code);
    }


    private void grantPermission(Long roleId, String code) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_permission WHERE perm_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        Long permId;
        if (id == null) {
            jdbcTemplate.update("INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, "
                    + "sort, visible, status) VALUES (0, ?, ?, 'API', 98, 1, 1)", code, code);
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

    private Map<String, Object> loginFull(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        ResponseEntity<Result> response = restTemplate.postForEntity("/api/auth/login", request, Result.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (Map<String, Object>) response.getBody().getData();
    }

    private String loginToken(String username, String password) {
        return (String) loginFull(username, password).get("accessToken");
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
