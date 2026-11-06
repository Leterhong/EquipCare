package com.equipcare.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内消息。
 */
@Data
@TableName("sys_message")
public class SysMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long receiverId;

    private String title;

    private String content;

    /** TASK / WORKORDER / SLA / SYSTEM */
    private String msgType;

    private String bizType;

    private Long bizId;

    /** 0 未读 1 已读 */
    private Integer readFlag;

    private LocalDateTime readTime;

    /** SENT / FAILED / DEAD */
    private String sendStatus;

    private LocalDateTime createTime;
}
