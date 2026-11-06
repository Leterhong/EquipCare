package com.equipcare.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点。
 */
@Data
@Schema(description = "权限树节点")
public class PermissionTreeVO {

    private Long id;

    private Long parentId;

    private String code;

    private String name;

    private String type;

    private String path;

    private Integer sort;

    private List<PermissionTreeVO> children = new ArrayList<>();
}
