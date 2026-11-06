package com.equipcare.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节点。
 */
@Data
@Schema(description = "部门树节点")
public class DeptTreeVO {

    private Long id;

    private Long parentId;

    private String deptName;

    private String deptCode;

    private Long leaderUserId;

    private Integer sort;

    private Integer status;

    private List<DeptTreeVO> children = new ArrayList<>();
}
