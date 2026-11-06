package com.equipcare.security.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点，供前端动态路由使用。
 */
@Data
@Schema(description = "菜单节点")
public class MenuVO {

    @Schema(description = "权限ID")
    private Long id;

    @Schema(description = "父级ID，0 为根")
    private Long parentId;

    @Schema(description = "权限码")
    private String code;

    @Schema(description = "菜单名称")
    private String name;

    @Schema(description = "路由路径")
    private String path;

    @Schema(description = "前端组件路径")
    private String component;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "排序号")
    private Integer sort;

    @Schema(description = "子菜单")
    private List<MenuVO> children = new ArrayList<>();
}
