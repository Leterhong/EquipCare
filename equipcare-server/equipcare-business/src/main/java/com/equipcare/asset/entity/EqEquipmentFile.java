package com.equipcare.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备图片 / 附件关联。
 *
 * <p>只做关联，不复制文件元数据；文件本体统一存在 {@code sys_file}。
 * 关联记录随设备物理删除级联清理（逻辑删除不触发）。
 */
@Data
@TableName("eq_equipment_file")
public class EqEquipmentFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long equipmentId;

    private Long fileId;

    /** IMAGE / ATTACHMENT，见 {@link com.equipcare.asset.enums.EquipmentFileType} */
    private String fileType;

    private Integer sortNo;

    private Long createBy;

    private LocalDateTime createTime;
}
