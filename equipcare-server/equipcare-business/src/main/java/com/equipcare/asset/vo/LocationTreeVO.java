package com.equipcare.asset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 位置树节点。
 */
@Data
@Schema(description = "位置树节点")
public class LocationTreeVO {

    private Long id;

    private Long parentId;

    private String locationCode;

    private String locationName;

    @Schema(description = "类型：PARK / BUILDING / FLOOR / ROOM")
    private String locationType;

    @Schema(description = "全路径，例如 园区A/1号楼/3层/配电室")
    private String fullPath;

    private Integer sort;

    @Schema(description = "状态：1 启用 0 停用")
    private Integer status;

    @Schema(description = "该位置下设备数量")
    private Integer equipmentCount;

    private List<LocationTreeVO> children = new ArrayList<>();
}
