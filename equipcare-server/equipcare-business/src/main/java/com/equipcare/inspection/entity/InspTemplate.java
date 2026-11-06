package com.equipcare.inspection.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.equipcare.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 巡检模板。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("insp_template")
public class InspTemplate extends BaseEntity {

    private String templateCode;

    private String templateName;

    private Long categoryId;

    private Integer version;

    /** DRAFT 草稿 / PUBLISHED 已发布 / DISABLED 已停用 */
    private String status;

    private Integer itemCount;

    private String description;

    private LocalDateTime publishTime;

    private Long publisherId;

    @TableLogic
    private Integer deleted;
}
