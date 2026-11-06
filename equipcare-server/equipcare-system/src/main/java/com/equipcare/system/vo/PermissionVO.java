package com.equipcare.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 权限平铺项，用于「权限管理」页面列表展示。
 *
 * <p>权限本身由代码与初始化脚本维护（权限码直接对应后端
 * {@code @PreAuthorize} 里的字符串），页面只读，避免运行期改权限码导致接口鉴权失效。
 */
@Data
@Schema(description = "权限项")
public class PermissionVO {

    private Long id;

    private Long parentId;

    @Schema(description = "权限码，与后端 @PreAuthorize 一一对应")
    private String code;

    private String name;

    @Schema(description = "MENU 菜单 / BUTTON 按钮 / API 纯接口")
    private String type;

    @Schema(description = "前端路由路径（仅 MENU）")
    private String path;

    @Schema(description = "前端组件标识（仅 MENU）")
    private String component;

    private String icon;

    private Integer sort;

    @Schema(description = "菜单是否可见")
    private Integer visible;

    @Schema(description = "1 启用 0 停用")
    private Integer status;

    @Schema(description = "已分配该权限的角色数")
    private Integer roleCount;
}
