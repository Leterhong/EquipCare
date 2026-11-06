package com.equipcare.notification.service;

import com.equipcare.common.result.PageResult;
import com.equipcare.notification.dto.MessageQuery;
import com.equipcare.notification.vo.MessageVO;

/**
 * 站内消息服务。
 */
public interface MessageService {

    /**
     * 发送站内消息。供巡检、工单、SLA 等模块调用。
     *
     * @param receiverId 接收人
     * @param title      标题
     * @param content    内容
     * @param msgType    类型：TASK / WORKORDER / SLA / SYSTEM
     * @param bizType    业务类型
     * @param bizId      业务主键，前端据此跳转详情
     */
    void send(Long receiverId, String title, String content, String msgType, String bizType, Long bizId);

    /** 我的消息分页 */
    PageResult<MessageVO> pageMine(MessageQuery query);

    /** 我的未读消息数 */
    long unreadCount();

    /** 标记单条已读（仅本人消息） */
    void markRead(Long id);

    /** 批量标记已读（仅本人消息，忽略不属于本人的 id 与已读消息） */
    void markReadBatch(java.util.List<Long> ids);

    /** 标记全部已读 */
    void markAllRead();
}
