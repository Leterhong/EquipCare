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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第三阶段集成测试：用户权限、设备资产、二维码、文件上传。
 *
 * <p>覆盖验收清单：
 * <ol>
 *   <li>真实数据库增删改查（设备创建 / 查询 / 状态变更 / 删除）；</li>
 *   <li>不同角色的接口权限（管理员 vs 巡检人员），验证后端而非前端拦截；</li>
 *   <li>二维码生成与扫码取数，且二维码内容不含敏感信息；</li>
 *   <li>设备编号唯一性（重复编号返回 2002）；</li>
 *   <li>文件上传、绑定、解绑与类型白名单；</li>
 *   <li>角色删除保护、个人资料修改。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SuppressWarnings("unchecked")
class Stage3IT {

    private static final String ADMIN_USERNAME = "stage3_admin";
    private static final String ADMIN_PASSWORD = "Stage3Admin123";
    private static final String INSPECTOR_USERNAME = "stage3_inspector";
    private static final String INSPECTOR_PASSWORD = "Stage3Insp123";
    private static final String TEST_ROLE_CODE = "STAGE3_TESTER";

    private static final String[] REQUIRED_PERMISSIONS = {
            "asset:equipment:view", "asset:equipment:create", "asset:equipment:update",
            "asset:equipment:delete", "asset:equipment:status", "asset:equipment:qrcode",
            "asset:category:manage",
            "system:user:view", "system:user:manage", "system:role:manage", "system:audit:view",
            "system:message:view", "health:view"
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String inspectorToken;
    private Long adminUserId;
    private Long inspectorUserId;
    private Long inspectorRoleId;
    private Long categoryId;
    private Long locationId;
    private Long equipmentId;
    private Long createdRoleId;
    private String assetNo;
    private String qrToken;

    /* ------------------------------------------------------------------ */
    /* 准备与清理                                                          */
    /* ------------------------------------------------------------------ */

    @BeforeAll
    void prepare() throws Exception {
        cleanup();

        // 角色：ADMIN 与 INSPECTOR
        Long adminRoleId = ensureRole("ADMIN", "系统管理员", "ALL", 1);
        inspectorRoleId = ensureRole("INSPECTOR", "巡检人员", "SELF", 0);

        for (String code : REQUIRED_PERMISSIONS) {
            Long permId = ensurePermission(code);
            jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                    adminRoleId, permId);
        }
        Long viewPermId = ensurePermission("asset:equipment:view");
        jdbcTemplate.update("INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                inspectorRoleId, viewPermId);

        adminUserId = createUser(ADMIN_USERNAME, ADMIN_PASSWORD, "三阶段管理员", adminRoleId);
        inspectorUserId = createUser(INSPECTOR_USERNAME, INSPECTOR_PASSWORD, "三阶段巡检员", inspectorRoleId);

        // 分类与位置
        String ts = String.valueOf(System.currentTimeMillis());
        jdbcTemplate.update("INSERT INTO eq_category (parent_id, category_code, category_name, sort, status) "
                + "VALUES (0, ?, '测试类别', 90, 1)", "T3" + ts.substring(ts.length() - 6));
        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_category WHERE category_code = ?", Long.class, "T3" + ts.substring(ts.length() - 6));
        jdbcTemplate.update("INSERT INTO eq_location (parent_id, location_code, location_name, location_type, full_path, sort, status) "
                + "VALUES (0, ?, '三阶段测试位置', 'ROOM', '测试园区/测试位置', 90, 1)", "T3LOC" + ts.substring(ts.length() - 6));
        locationId = jdbcTemplate.queryForObject(
                "SELECT id FROM eq_location WHERE location_code = ?", Long.class, "T3LOC" + ts.substring(ts.length() - 6));

        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        inspectorToken = login(INSPECTOR_USERNAME, INSPECTOR_PASSWORD);
    }

    @AfterAll
    void cleanupAll() {
        cleanup();
    }

    private void cleanup() {
        jdbcTemplate.update("DELETE FROM eq_equipment_file WHERE equipment_id IN "
                + "(SELECT id FROM eq_equipment WHERE asset_no LIKE 'T3-%' OR equipment_name LIKE '三阶段%')");
        jdbcTemplate.update("DELETE FROM eq_equipment WHERE asset_no LIKE 'T3-%' OR equipment_name LIKE '三阶段%'");
        jdbcTemplate.update("DELETE FROM sys_file WHERE biz_type = 'EQUIPMENT'");
        jdbcTemplate.update("DELETE ur FROM sys_user_role ur INNER JOIN sys_user u ON u.id = ur.user_id "
                + "WHERE u.username IN (?, ?)", ADMIN_USERNAME, INSPECTOR_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN (?, ?)", ADMIN_USERNAME, INSPECTOR_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN "
                + "(SELECT id FROM sys_role WHERE role_code IN (?, ?))", TEST_ROLE_CODE, "STAGE3_TESTER2");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code IN (?, ?)", TEST_ROLE_CODE, "STAGE3_TESTER2");
        jdbcTemplate.update("DELETE FROM eq_category WHERE category_code LIKE 'T3%'");
        jdbcTemplate.update("DELETE FROM eq_location WHERE location_code LIKE 'T3LOC%'");
    }

    private Long ensureRole(String code, String name, String scope, int builtin) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_role WHERE role_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        if (id != null) {
            return id.longValue();
        }
        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, data_scope, builtin, sort, status) "
                + "VALUES (?, ?, ?, ?, 50, 1)", code, name, scope, builtin);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class, code);
    }

    private Long ensurePermission(String code) {
        Integer id = jdbcTemplate.query("SELECT id FROM sys_permission WHERE perm_code = ?",
                rs -> rs.next() ? rs.getInt("id") : null, code);
        if (id != null) {
            return id.longValue();
        }
        jdbcTemplate.update("INSERT INTO sys_permission (parent_id, perm_code, perm_name, perm_type, sort, visible, status) "
                + "VALUES (0, ?, ?, 'API', 90, 1, 1)", code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_permission WHERE perm_code = ?", Long.class, code);
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
        assertThat(response.getBody().getCode()).isEqualTo(200);
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

    private Long createEquipment(String name, String fixedAssetNo) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("equipmentName", name);
        payload.put("categoryId", categoryId);
        payload.put("locationId", locationId);
        payload.put("brand", "测试品牌");
        payload.put("model", "T3-100");
        if (fixedAssetNo != null) {
            payload.put("assetNo", fixedAssetNo);
        }
        ResponseEntity<Result> response = call(HttpMethod.POST, "/api/asset/equipment", adminToken, payload);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(codeOf(response)).isEqualTo(200);
        return ((Number) response.getBody().getData()).longValue();
    }

    /* ------------------------------------------------------------------ */
    /* 用例                                                                */
    /* ------------------------------------------------------------------ */

    @Test
    @Order(1)
    @DisplayName("1. 接口权限由后端校验：巡检员访问用户管理返回 403")
    void permissionEnforcedForInspector() {
        ResponseEntity<Result> adminUsers = call(HttpMethod.GET, "/api/system/users?pageNum=1&pageSize=5", adminToken, null);
        assertThat(adminUsers.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(codeOf(adminUsers)).isEqualTo(200);

        ResponseEntity<Result> inspectorUsers = call(HttpMethod.GET, "/api/system/users?pageNum=1&pageSize=5", inspectorToken, null);
        assertThat(inspectorUsers.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(codeOf(inspectorUsers)).isEqualTo(403);

        // 巡检员有设备查看权限，列表可访问
        ResponseEntity<Result> inspectorEquipments = call(HttpMethod.GET, "/api/asset/equipment?pageNum=1&pageSize=5", inspectorToken, null);
        assertThat(inspectorEquipments.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 但没有新增与二维码权限
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("equipmentName", "三阶段越权设备");
        payload.put("categoryId", categoryId);
        payload.put("locationId", locationId);
        ResponseEntity<Result> create = call(HttpMethod.POST, "/api/asset/equipment", inspectorToken, payload);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Result> qr = call(HttpMethod.GET, "/api/asset/equipment/1/qrcode", inspectorToken, null);
        assertThat(qr.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 未登录直接 401
        ResponseEntity<Result> anonymous = call(HttpMethod.GET, "/api/asset/equipment?pageNum=1&pageSize=5", null, null);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    @DisplayName("2. 资产编号唯一：重复编号返回 2002，留空自动生成")
    void assetNoMustBeUnique() {
        String ts = String.valueOf(System.currentTimeMillis());
        assetNo = "T3-" + ts.substring(ts.length() - 8);
        equipmentId = createEquipment("三阶段测试空调", assetNo);

        ResponseEntity<Result> duplicate = call(HttpMethod.POST, "/api/asset/equipment", adminToken,
                Map.of("equipmentName", "三阶段重复编号设备", "categoryId", categoryId,
                        "locationId", locationId, "assetNo", assetNo));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(duplicate)).isEqualTo(2002);

        Long autoId = createEquipment("三阶段自动编号设备", null);
        String generated = jdbcTemplate.queryForObject(
                "SELECT asset_no FROM eq_equipment WHERE id = ?", String.class, autoId);
        assertThat(generated).isNotBlank().startsWith("T3").contains("-");
    }

    @Test
    @Order(3)
    @DisplayName("3. 设备详情、状态变更与校验")
    void equipmentLifecycle() {
        ResponseEntity<Result> detail = call(HttpMethod.GET, "/api/asset/equipment/" + equipmentId, adminToken, null);
        assertThat(codeOf(detail)).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) detail.getBody().getData();
        assertThat(data.get("assetNo")).isEqualTo(assetNo);
        assertThat(data.get("status")).isEqualTo("IN_USE");
        assertThat(data.keySet()).contains("files", "recentInspections", "repairHistory", "repairCount");
        qrToken = (String) data.get("qrToken");

        // 报废必须填原因
        ResponseEntity<Result> noReason = call(HttpMethod.PATCH, "/api/asset/equipment/" + equipmentId + "/status",
                adminToken, Map.of("status", "SCRAPPED"));
        assertThat(noReason.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 维修中不能手工设置
        ResponseEntity<Result> illegal = call(HttpMethod.PATCH, "/api/asset/equipment/" + equipmentId + "/status",
                adminToken, Map.of("status", "REPAIRING", "reason", "试图绕过工单"));
        // 业务规则拒绝统一返回 2004（HTTP 409），与「无权限」的 403 区分开
        assertThat(illegal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(illegal)).isEqualTo(2004);

        // 停用
        ResponseEntity<Result> idle = call(HttpMethod.PATCH, "/api/asset/equipment/" + equipmentId + "/status",
                adminToken, Map.of("status", "IDLE", "reason", "测试停用"));
        assertThat(codeOf(idle)).isEqualTo(200);
        String status = jdbcTemplate.queryForObject("SELECT status FROM eq_equipment WHERE id = ?", String.class, equipmentId);
        assertThat(status).isEqualTo("IDLE");
    }

    @Test
    @Order(4)
    @DisplayName("4. 二维码：ZXing 生成 PNG，内容不含敏感信息，扫码需权限")
    void qrCodeGenerationAndScan() {
        ResponseEntity<Result> content = call(HttpMethod.GET, "/api/asset/equipment/" + equipmentId + "/qrcode", adminToken, null);
        assertThat(codeOf(content)).isEqualTo(200);
        Map<String, Object> data = (Map<String, Object>) content.getBody().getData();
        String text = (String) data.get("content");
        assertThat(text).contains("token=" + qrToken);
        assertThat(text.toLowerCase()).doesNotContain("password", "jwt", "secret", "bearer");
        assertThat(data.get("imageUrl")).isEqualTo("/api/asset/equipment/" + equipmentId + "/qrcode.png");

        HttpHeaders headers = authHeaders(adminToken);
        ResponseEntity<byte[]> png = restTemplate.exchange(
                "/api/asset/equipment/" + equipmentId + "/qrcode.png?size=240",
                HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(png.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(png.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        byte[] body = png.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(200);
        assertThat(new byte[]{body[0], body[1], body[2], body[3], body[4], body[5], body[6], body[7]})
                .containsExactly((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                        (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A);

        // 巡检员扫码可以拿到设备档案
        ResponseEntity<Result> scan = call(HttpMethod.GET,
                "/api/asset/equipment/scan?token=" + qrToken, inspectorToken, null);
        assertThat(codeOf(scan)).isEqualTo(200);
        Map<String, Object> scanData = (Map<String, Object>) scan.getBody().getData();
        assertThat(((Number) scanData.get("id")).longValue()).isEqualTo(equipmentId);
        assertThat(scanData.get("statusLabel")).isEqualTo("闲置");
        assertThat(scanData.get("scannedBy")).isEqualTo("三阶段巡检员");
        assertThat(scanData.keySet()).contains("recentInspections", "repairHistory");

        // 无效令牌
        ResponseEntity<Result> notFound = call(HttpMethod.GET,
                "/api/asset/equipment/scan?token=not-exists-token", inspectorToken, null);
        assertThat(codeOf(notFound)).isEqualTo(2001);
    }

    @Test
    @Order(5)
    @DisplayName("5. 文件上传与绑定：白名单生效，绑定后可查询与解绑")
    void fileUploadAndBind() throws Exception {
        byte[] png = samplePng();
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(png) {
            @Override
            public String getFilename() {
                return "device.png";
            }
        });
        form.add("bizType", "EQUIPMENT");
        form.add("bizId", String.valueOf(equipmentId));

        HttpHeaders headers = authHeaders(adminToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<Result> upload = restTemplate.postForEntity(
                "/api/files/upload", new HttpEntity<>(form, headers), Result.class);
        assertThat(codeOf(upload)).isEqualTo(200);
        Map<String, Object> fileData = (Map<String, Object>) upload.getBody().getData();
        Long fileId = ((Number) fileData.get("fileId")).longValue();

        ResponseEntity<Result> bind = call(HttpMethod.POST, "/api/asset/equipment/" + equipmentId + "/files",
                adminToken, Map.of("fileId", fileId, "fileType", "IMAGE"));
        assertThat(codeOf(bind)).isEqualTo(200);
        Map<String, Object> bound = (Map<String, Object>) bind.getBody().getData();
        assertThat(bound.get("fileType")).isEqualTo("IMAGE");
        assertThat(bound.get("fileName")).isEqualTo("device.png");

        ResponseEntity<Result> files = call(HttpMethod.GET, "/api/asset/equipment/" + equipmentId + "/files",
                adminToken, null);
        List<?> list = (List<?>) files.getBody().getData();
        assertThat(list).hasSize(1);

        // 详情里应带出封面地址
        ResponseEntity<Result> detail = call(HttpMethod.GET, "/api/asset/equipment/" + equipmentId, adminToken, null);
        Map<String, Object> detailData = (Map<String, Object>) detail.getBody().getData();
        assertThat(detailData.get("coverUrl")).isNotNull();

        ResponseEntity<Result> unbind = call(HttpMethod.DELETE,
                "/api/asset/equipment/" + equipmentId + "/files/" + fileId, adminToken, null);
        assertThat(codeOf(unbind)).isEqualTo(200);
        ResponseEntity<Result> after = call(HttpMethod.GET, "/api/asset/equipment/" + equipmentId + "/files",
                adminToken, null);
        assertThat((List<?>) after.getBody().getData()).isEmpty();

        // 非白名单扩展名被拒绝（bizType 合法，让扩展名校验在 store() 内触发 5002）
        MultiValueMap<String, Object> badForm = new LinkedMultiValueMap<>();
        badForm.add("file", new ByteArrayResource("MZ".getBytes()) {
            @Override
            public String getFilename() {
                return "virus.exe";
            }
        });
        badForm.add("bizType", "EQUIPMENT");
        ResponseEntity<Result> bad = restTemplate.postForEntity(
                "/api/files/upload", new HttpEntity<>(badForm, headers), Result.class);
        assertThat(codeOf(bad)).isEqualTo(5002);
    }

    @Test
    @Order(6)
    @DisplayName("6. 角色删除：内置角色与有用户占用的角色被拒绝")
    void roleDeleteGuards() {
        ResponseEntity<Result> created = call(HttpMethod.POST, "/api/system/roles", adminToken,
                Map.of("roleCode", TEST_ROLE_CODE, "roleName", "三阶段临时角色", "dataScope", "SELF", "sort", 88));
        assertThat(codeOf(created)).isEqualTo(200);
        createdRoleId = ((Number) created.getBody().getData()).longValue();

        // 有用户占用：把巡检员绑到该角色
        jdbcTemplate.update("INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                inspectorUserId, createdRoleId);
        ResponseEntity<Result> inUse = call(HttpMethod.DELETE, "/api/system/roles/" + createdRoleId, adminToken, null);
        assertThat(inUse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(inUse)).isEqualTo(2003);

        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ? AND role_id = ?",
                inspectorUserId, createdRoleId);
        ResponseEntity<Result> deleted = call(HttpMethod.DELETE, "/api/system/roles/" + createdRoleId, adminToken, null);
        assertThat(codeOf(deleted)).isEqualTo(200);

        // 内置角色（builtin=1）不可删除
        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, data_scope, builtin, sort, status) "
                + "VALUES ('STAGE3_TESTER2', '三阶段内置角色', 'ALL', 1, 89, 1)");
        Long builtinRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = 'STAGE3_TESTER2'", Long.class);
        ResponseEntity<Result> builtin = call(HttpMethod.DELETE, "/api/system/roles/" + builtinRoleId, adminToken, null);
        assertThat(builtin.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(codeOf(builtin)).isEqualTo(2004);

        // 权限清单只读接口
        ResponseEntity<Result> permissions = call(HttpMethod.GET, "/api/system/permissions", adminToken, null);
        assertThat(codeOf(permissions)).isEqualTo(200);
        List<?> perms = (List<?>) permissions.getBody().getData();
        assertThat(perms).isNotEmpty();
        Map<String, Object> first = (Map<String, Object>) perms.get(0);
        assertThat(first.keySet()).contains("code", "name", "type", "roleCount");
    }

    @Test
    @Order(7)
    @DisplayName("7. 个人资料修改与手机号校验")
    void updateProfile() {
        ResponseEntity<Result> ok = call(HttpMethod.PUT, "/api/auth/profile", inspectorToken,
                Map.of("realName", "三阶段巡检员", "phone", "13800001234", "email", "inspector@example.com"));
        assertThat(codeOf(ok)).isEqualTo(200);
        Map<String, Object> me = (Map<String, Object>) ok.getBody().getData();
        assertThat(me.get("phone")).isEqualTo("13800001234");

        ResponseEntity<Result> invalid = call(HttpMethod.PUT, "/api/auth/profile", inspectorToken,
                Map.of("realName", "三阶段巡检员", "phone", "123"));
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Result> current = call(HttpMethod.GET, "/api/auth/me", inspectorToken, null);
        Map<String, Object> currentData = (Map<String, Object>) current.getBody().getData();
        assertThat(currentData.get("email")).isEqualTo("inspector@example.com");
    }

    @Test
    @Order(8)
    @DisplayName("8. 设备逻辑删除后列表不再返回")
    void softDeleteEquipment() {
        ResponseEntity<Result> deleted = call(HttpMethod.DELETE, "/api/asset/equipment/" + equipmentId, adminToken, null);
        assertThat(codeOf(deleted)).isEqualTo(200);

        ResponseEntity<Result> detail = call(HttpMethod.GET, "/api/asset/equipment/" + equipmentId, adminToken, null);
        assertThat(codeOf(detail)).isEqualTo(2001);

        Integer deletedFlag = jdbcTemplate.queryForObject(
                "SELECT deleted FROM eq_equipment WHERE id = ?", Integer.class, equipmentId);
        assertThat(deletedFlag).isEqualTo(1);
    }

    private byte[] samplePng() throws Exception {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                image.setRGB(x, y, (x / 4 + y / 4) % 2 == 0 ? 0x2F6FB3 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }
}
