package com.equipcare.notification.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息展示对象。
 */
@Data
@Schema(description = "站内消息")
public class MessageVO {

    private Long id;

    private String title;

    private String content;

    private String msgType;

    private String bizType;

    private Long bizId;

    private Integer readFlag;

    private LocalDateTime createTime;
}
