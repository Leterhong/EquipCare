package com.equipcare.system.controller;

import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.file.FileStorageProperties;
import com.equipcare.common.result.Result;
import com.equipcare.common.result.ResultCode;
import com.equipcare.common.security.LoginUser;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.system.entity.SysFile;
import com.equipcare.system.service.FileStorageService;
import com.equipcare.system.vo.FileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传与下载。
 *
 * <p>访问控制（阶段十二整改，修复下载水平越权）：
 * <ul>
 *   <li>上传：bizType 白名单 + 按业务域映射操作权限（设备→设备编辑、巡检→巡检执行、工单→报修/维修）；</li>
 *   <li>下载：上传人本人、ADMIN/EQUIP_ADMIN 放行；其他用户必须持有附件所属业务域的查看权限，
 *       未知 bizType 一律拒绝，防止按自增 id 枚举下载他人附件。</li>
 * </ul>
 */
@Tag(name = "04. 文件", description = "附件上传与下载（需登录 + 业务域权限）")
@RestController
@RequestMapping("/api/files")
public class FileController {

    /** 上传业务域白名单 → 所需操作权限（工单场景报修/维修两条路径都会传照片）。 */
    private static final Map<String, Set<String>> UPLOAD_PERMISSIONS = Map.of(
            "EQUIPMENT", Set.of("asset:equipment:update"),
            "INSPECT", Set.of("inspect:task:execute"),
            "WORK_ORDER", Set.of("wo:workorder:create", "wo:workorder:repair"));

    /** 下载业务域 → 所需查看权限。 */
    private static final Map<String, String> DOWNLOAD_PERMISSIONS = Map.of(
            "EQUIPMENT", "asset:equipment:view",
            "INSPECT", "inspect:task:view",
            "WORK_ORDER", "wo:workorder:view");

    private final FileStorageService fileStorageService;
    private final FileStorageProperties properties;

    public FileController(FileStorageService fileStorageService, FileStorageProperties properties) {
        this.fileStorageService = fileStorageService;
        this.properties = properties;
    }

    @Operation(summary = "上传文件", description = "白名单：jpg/jpeg/png/pdf/docx/xlsx，单文件不超过 10MB；bizType 须为 EQUIPMENT/INSPECT/WORK_ORDER 且持对应业务权限")
    @PostMapping("/upload")
    @AuditLog(module = "文件", action = "上传", saveParam = false)
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam(value = "bizType", required = false) String bizType,
                                 @RequestParam(value = "bizId", required = false) Long bizId) {
        requireUploadPermission(bizType);
        return Result.ok(fileStorageService.store(file, bizType, bizId));
    }

    @Operation(summary = "下载/预览文件", description = "上传人/管理员/所属业务域持查看权限者可下载；校验路径防目录穿越")
    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        SysFile file = fileStorageService.getById(id);
        requireDownloadPermission(file);
        Path root = Paths.get(properties.getUploadDir()).toAbsolutePath().normalize();
        Path target = root.resolve(file.getFilePath()).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "非法的文件路径");
        }
        Resource resource = new FileSystemResource(target);
        if (!resource.exists()) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "文件已丢失");
        }
        String encodedName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = file.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(file.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    /** 上传权限：bizType 必须在白名单内且当前用户持对应业务操作权限。 */
    private void requireUploadPermission(String bizType) {
        if (bizType == null || !UPLOAD_PERMISSIONS.containsKey(bizType)) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的业务类型：" + bizType);
        }
        LoginUser user = SecurityUtils.getLoginUser();
        boolean allowed = UPLOAD_PERMISSIONS.get(bizType).stream().anyMatch(user::hasPermission);
        if (!allowed) {
            throw new BizException(ResultCode.FORBIDDEN, "没有上传" + bizType + "业务附件的权限");
        }
    }

    /** 下载权限：上传人本人 / 管理员 / 所属业务域查看权限，三路放行其一。 */
    private void requireDownloadPermission(SysFile file) {
        if (file == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "文件不存在");
        }
        LoginUser user = SecurityUtils.getLoginUser();
        if (user.getUserId() != null && user.getUserId().equals(file.getUploadUserId())) {
            return;
        }
        if (user.hasRole("ADMIN") || user.hasRole("EQUIP_ADMIN")) {
            return;
        }
        String required = DOWNLOAD_PERMISSIONS.get(file.getBizType());
        if (required == null || !user.hasPermission(required)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该附件");
        }
    }
}
