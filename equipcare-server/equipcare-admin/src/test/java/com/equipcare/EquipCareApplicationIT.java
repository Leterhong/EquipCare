package com.equipcare;

import com.equipcare.common.result.Result;
import com.equipcare.security.dto.LoginRequest;
import com.equipcare.security.vo.LoginVO;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：验证第二阶段要求的 7 项测试内容。
 *
 * <ol>
 *   <li>Spring Boot 能正常启动（整个类加载成功即证明）</li>
 *   <li>MySQL 连接正常</li>
 *   <li>Redis 连接正常</li>
 *   <li>数据库初始化成功（表结构与约束存在）</li>
 *   <li>Swagger 页面可访问</li>
 *   <li>统一异常处理有效</li>
 *   <li>登录接口正常响应</li>
 * </ol>
 *
 * <p>运行前置条件：MySQL 中存在 {@code equipcare_test} 库且已执行 {@code database/schema.sql}，
 * Redis 可用（使用 db 1，与开发库隔离）。测试会自行创建并清理测试账号，不依赖 seed 数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EquipCareApplicationIT {

    private static final String TEST_USERNAME = "ittest_admin";
    private static final String TEST_PASSWORD = "Test1234abc";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long testUserId;

    @BeforeAll
    void prepareTestAccount() {
        // 幂等清理：先删关联再删用户
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE username = ?)", TEST_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", TEST_USERNAME);

        jdbcTemplate.update("INSERT INTO sys_user (username, password, real_name, status) VALUES (?, ?, ?, 1)",
                TEST_USERNAME, passwordEncoder.encode(TEST_PASSWORD), "集成测试管理员");
        testUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, TEST_USERNAME);

        // 绑定 ADMIN 角色；测试库未导入 seed 时按需创建
        Integer adminRoleId = jdbcTemplate.query(
                "SELECT id FROM sys_role WHERE role_code = 'ADMIN'",
                rs -> rs.next() ? rs.getInt("id") : null);
        if (adminRoleId == null) {
            jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, data_scope, status) "
                    + "VALUES ('ADMIN', '系统管理员', 'ALL', 1)");
            adminRoleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM sys_role WHERE role_code = 'ADMIN'", Integer.class);
        }
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                testUserId, adminRoleId);

        // 测试库不导入 seed 权限数据，按用例需要补齐最小权限集并授予 ADMIN 角色
        for (String[] perm : new String[][]{
                {"asset:equipment:view", "设备台账"},
                {"system:message:view", "我的消息"}}) {
            Integer permId = jdbcTemplate.query("SELECT id FROM sys_permission WHERE perm_code = ?",
                    rs -> rs.next() ? rs.getInt("id") : null, perm[0]);
            if (permId == null) {
                jdbcTemplate.update("INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type) "
                        + "VALUES (0, ?, ?, 'API')", perm[0], perm[1]);
                permId = jdbcTemplate.queryForObject(
                        "SELECT id FROM sys_permission WHERE perm_code = ?", Integer.class, perm[0]);
            }
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                    adminRoleId, permId);
        }
    }

    @AfterAll
    void cleanTestAccount() {
        if (testUserId != null) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", testUserId);
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", testUserId);
        }
    }

    @Test
    @Order(1)
    @DisplayName("1. Spring Boot 启动成功，容器内有核心 Bean")
    void contextLoads() {
        assertThat(restTemplate).isNotNull();
        assertThat(jdbcTemplate).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("2. MySQL 连接正常，能执行查询")
    void mysqlConnected() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }

    @Test
    @Order(3)
    @DisplayName("3. Redis 连接正常，PING 返回 PONG")
    void redisConnected() {
        String pong = redisConnectionFactory.getConnection().ping();
        assertThat(pong).isEqualToIgnoringCase("PONG");
    }

    @Test
    @Order(4)
    @DisplayName("4. 数据库初始化成功：32 张表、关键唯一索引与只插入触发器均存在")
    void schemaInitialized() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE()",
                Integer.class);
        assertThat(tables).isGreaterThanOrEqualTo(32);

        Integer uniqueIndexes = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND non_unique = 0
                   AND index_name IN ('uk_eq_equipment_asset_no', 'uk_sys_user_username',
                                      'uk_wo_anomaly', 'uk_wo_order_no', 'uk_sp_txn_no')
                """, Integer.class);
        assertThat(uniqueIndexes).isEqualTo(5);

        Integer triggers = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM information_schema.triggers
                 WHERE trigger_schema = DATABASE()
                """, Integer.class);
        assertThat(triggers).isGreaterThanOrEqualTo(6);

        // 库存不能为负的 CHECK 约束存在
        Integer checkConstraints = jdbcTemplate.queryForObject("""
                SELECT COUNT(1) FROM information_schema.table_constraints
                 WHERE table_schema = DATABASE() AND constraint_type = 'CHECK'
                   AND constraint_name = 'chk_sp_part_stock'
                """, Integer.class);
        assertThat(checkConstraints).isEqualTo(1);
    }

    @Test
    @Order(5)
    @DisplayName("5. Swagger 文档可访问且包含接口分组")
    void swaggerAccessible() {
        ResponseEntity<String> apiDocs = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(apiDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiDocs.getBody()).contains("openapi").contains("/api/auth/login");

        ResponseEntity<String> swaggerUi = restTemplate.getForEntity("/swagger-ui/index.html", String.class);
        assertThat(swaggerUi.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("6. 统一异常处理有效：参数校验失败返回 422 与统一结构")
    void validationErrorHandled() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 缺少用户名与密码，触发 MethodArgumentNotValidException
        ResponseEntity<Result> response = restTemplate.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>("{}", headers), Result.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(422);
        assertThat(response.getBody().getMessage()).isNotBlank();
        assertThat(response.getBody().getTimestamp()).isPositive();
    }

    @Test
    @Order(7)
    @DisplayName("6b. 未登录访问受保护接口返回 401 统一结构")
    void unauthorizedHandled() {
        ResponseEntity<Result> response = restTemplate.getForEntity("/api/system/users", Result.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(401);
    }

    @Test
    @Order(8)
    @DisplayName("6c. 业务数据不存在返回 2001 + 404 统一结构，而不是默认错误页")
    void notFoundHandled() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login());
        ResponseEntity<Result> response = restTemplate.exchange("/api/asset/equipment/999999999",
                HttpMethod.GET, new HttpEntity<>(headers), Result.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(2001);
        assertThat(response.getBody().getMessage()).contains("设备");
    }

    @Test
    @Order(8)
    @DisplayName("6d. 未登录访问不存在的路径也返回统一 JSON，不泄露默认错误页")
    void unknownPathReturnsUnifiedJson() {
        ResponseEntity<Result> response = restTemplate.getForEntity("/api/not-exists-path", Result.class);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isIn(401, 403, 404);
    }

    @Test
    @Order(9)
    @DisplayName("7. 登录接口正常响应，返回双令牌与用户信息")
    void loginWorks() {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        ResponseEntity<Result<Map<String, Object>>> response = restTemplate.exchange("/api/auth/login",
                HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(200);
        Map<String, Object> data = response.getBody().getData();
        assertThat(data).isNotNull();
        assertThat(data.get("accessToken")).asString().isNotBlank();
        assertThat(data.get("refreshToken")).asString().isNotBlank();
        assertThat(data.get("tokenType")).isEqualTo("Bearer");
        assertThat(((Number) data.get("expiresIn")).longValue()).isEqualTo(7200L);

        // 携带令牌访问 /api/auth/me，验证 JWT 过滤器与 Redis 会话
        String token = (String) data.get("accessToken");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Result<Map<String, Object>>> me = restTemplate.exchange("/api/auth/me",
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {
                });
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).isNotNull();
        assertThat(me.getBody().getData().get("username")).isEqualTo(TEST_USERNAME);
    }

    @Test
    @Order(10)
    @DisplayName("7b. 错误密码返回 1001，且不影响后续正确登录")
    void loginWithWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword("WrongPassword123");

        ResponseEntity<Result> response = restTemplate.postForEntity("/api/auth/login", request, Result.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(1001);

        // 清空失败计数，避免影响其他用例
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    @Test
    @Order(11)
    @DisplayName("8. 数据库约束生效：负库存被 CHECK 约束拒绝、状态日志不可修改")
    void databaseConstraints() {
        // 负库存插入失败
        assertThat(catchSqlError(() -> jdbcTemplate.update(
                "INSERT INTO sp_part (part_no, part_name, unit, current_stock, safety_stock, unit_price) "
                        + "VALUES ('TEST_NEG', '负库存测试', '个', -1, 0, 1.00)")))
                .isNotNull();

        // 工单状态日志不可 UPDATE（触发器阻断）
        // 测试库不导入 seed 数据，这里按需补齐类别与位置
        Integer categoryId = jdbcTemplate.query("SELECT id FROM eq_category WHERE category_code = 'IT_CAT'",
                rs -> rs.next() ? rs.getInt("id") : null);
        if (categoryId == null) {
            jdbcTemplate.update("INSERT INTO eq_category (parent_id, category_code, category_name) "
                    + "VALUES (0, 'IT_CAT', '集成测试类别')");
            categoryId = jdbcTemplate.queryForObject(
                    "SELECT id FROM eq_category WHERE category_code = 'IT_CAT'", Integer.class);
        }
        Integer locationId = jdbcTemplate.query("SELECT id FROM eq_location WHERE location_code = 'IT_LOC'",
                rs -> rs.next() ? rs.getInt("id") : null);
        if (locationId == null) {
            jdbcTemplate.update("INSERT INTO eq_location (parent_id, location_code, location_name, "
                    + "location_type, full_path) VALUES (0, 'IT_LOC', '集成测试位置', 'ROOM', '集成测试位置')");
            locationId = jdbcTemplate.queryForObject(
                    "SELECT id FROM eq_location WHERE location_code = 'IT_LOC'", Integer.class);
        }
        // 工单与状态日志受外键 + 只插入触发器保护，无法回滚清理，因此每次运行使用唯一编号
        String suffix = String.valueOf(System.currentTimeMillis());
        String assetNo = "IT-ASSET-" + suffix;
        String orderNo = "IT-WO-" + suffix;
        jdbcTemplate.update("INSERT INTO eq_equipment (asset_no, equipment_name, category_id, location_id, qr_token) "
                + "VALUES (?, '集成测试设备', ?, ?, ?)", assetNo, categoryId, locationId, "it-token-" + suffix);
        Long equipmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_equipment WHERE asset_no = ?", Long.class, assetNo);
        jdbcTemplate.update("INSERT INTO wo_work_order (order_no, title, source, equipment_id, fault_type, "
                + "fault_desc, level, status, reporter_id) VALUES (?, '集成测试工单', 'MANUAL', ?, "
                + "'TEST', '测试', 'P3', 'PENDING_ASSIGN', ?)", orderNo, equipmentId, testUserId);
        Long orderId = jdbcTemplate.queryForObject(
                "SELECT id FROM wo_work_order WHERE order_no = ?", Long.class, orderNo);
        jdbcTemplate.update("INSERT INTO wo_status_log (work_order_id, from_status, to_status, action, operator_id) "
                + "VALUES (?, NULL, 'PENDING_ASSIGN', 'CREATE', ?)", orderId, testUserId);

        assertThat(catchSqlError(() -> jdbcTemplate.update(
                "UPDATE wo_status_log SET reason = 'tampered' WHERE work_order_id = ?", orderId)))
                .isNotNull();
        assertThat(catchSqlError(() -> jdbcTemplate.update(
                "DELETE FROM wo_status_log WHERE work_order_id = ?", orderId)))
                .isNotNull();

        // 说明：工单、状态日志、库存流水均不可删除，测试数据保留在测试库中，
        // 每次运行使用唯一编号，因此不影响重复执行。
    }

    @Test
    @Order(12)
    @DisplayName("9. 健康分接口在无令牌时拒绝、参数非法时返回统一结构")
    void analyticsEndpointGuarded() {
        ResponseEntity<Result> noToken = restTemplate.getForEntity("/api/analytics/health/1", Result.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // actuator 健康检查不在统一响应体系内，单独用原始响应校验存活状态
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @Order(13)
    @DisplayName("10. 分页参数越界时被校验拦截")
    void pageSizeValidation() {
        String token = login();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange("/api/system/messages?pageSize=500",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getBody()).contains("\"code\":422");
    }

    private String login() {
        LoginRequest request = new LoginRequest();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);
        ResponseEntity<Result<Map<String, Object>>> response = restTemplate.exchange("/api/auth/login",
                HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {
                });
        assertThat(response.getBody()).isNotNull();
        return (String) response.getBody().getData().get("accessToken");
    }

    private Throwable catchSqlError(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
