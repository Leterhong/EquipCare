package com.equipcare.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.realtime.SseHub;

import java.util.Map;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.ResultCode;
import com.equipcare.notification.dto.MessageQuery;
import com.equipcare.notification.entity.SysMessage;
import com.equipcare.notification.mapper.SysMessageMapper;
import com.equipcare.notification.service.MessageService;
import com.equipcare.notification.vo.MessageVO;
import com.equipcare.common.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内消息实现。
 *
 * <p>消息只与接收人绑定，查询与标记已读均强制校验归属，防止越权读取他人消息。
 */
@Service
public class MessageServiceImpl implements MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);

    private final SysMessageMapper messageMapper;
    private final SseHub sseHub;

    public MessageServiceImpl(SysMessageMapper messageMapper, SseHub sseHub) {
        this.messageMapper = messageMapper;
        this.sseHub = sseHub;
    }

    @Override
    public void send(Long receiverId, String title, String content,
                     String msgType, String bizType, Long bizId) {
        if (receiverId == null) {
            return;
        }
        SysMessage message = new SysMessage();
        message.setReceiverId(receiverId);
        message.setTitle(title);
        message.setContent(content);
        message.setMsgType(msgType);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setReadFlag(0);
        message.setSendStatus("SENT");
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
        log.debug("发送站内消息 receiver={} type={} biz={}#{}", receiverId, msgType, bizType, bizId);
        // SSE 实时推送（尽力而为，失败不影响消息落库；前端铃铛据此即时刷新）
        try {
            sseHub.sendToUser(receiverId, "message:new", String.valueOf(message.getId()),
                    Map.of("id", message.getId(), "title", title == null ? "" : title,
                            "msgType", msgType == null ? "" : msgType,
                            "bizType", bizType == null ? "" : bizType,
                            "bizId", bizId == null ? 0L : bizId));
        } catch (Exception e) {
            log.debug("SSE 推送未送达 receiver={}", receiverId);
        }
    }

    @Override
    public PageResult<MessageVO> pageMine(MessageQuery query) {
        Long userId = SecurityUtils.getCurrentUserId();
        LambdaQueryWrapper<SysMessage> wrapper = Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getReceiverId, userId)
                .eq(query.getMsgType() != null && !query.getMsgType().isBlank(),
                        SysMessage::getMsgType, query.getMsgType())
                .eq(query.getReadFlag() != null, SysMessage::getReadFlag, query.getReadFlag())
                .orderByDesc(SysMessage::getId);
        IPage<SysMessage> page = messageMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<MessageVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public long unreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        return messageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getReceiverId, userId)
                .eq(SysMessage::getReadFlag, 0));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id) {
        SysMessage message = messageMapper.selectById(id);
        if (message == null) {
            throw new BizException(ResultCode.DATA_NOT_FOUND, "消息不存在");
        }
        if (!message.getReceiverId().equals(SecurityUtils.getCurrentUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作他人消息");
        }
        if (message.getReadFlag() != null && message.getReadFlag() == 1) {
            return;
        }
        SysMessage update = new SysMessage();
        update.setId(id);
        update.setReadFlag(1);
        update.setReadTime(LocalDateTime.now());
        messageMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReadBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "请先勾选要标记的消息");
        }
        if (ids.size() > 200) {
            throw new BizException(ResultCode.BAD_REQUEST, "单次最多标记 200 条");
        }
        // receiver 条件保证越权 id 直接落到 0 行更新，而不是抛错打断批量操作
        SysMessage update = new SysMessage();
        update.setReadFlag(1);
        update.setReadTime(LocalDateTime.now());
        messageMapper.update(update, Wrappers.<SysMessage>lambdaUpdate()
                .eq(SysMessage::getReceiverId, SecurityUtils.getCurrentUserId())
                .eq(SysMessage::getReadFlag, 0)
                .in(SysMessage::getId, ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        SysMessage update = new SysMessage();
        update.setReadFlag(1);
        update.setReadTime(LocalDateTime.now());
        messageMapper.update(update, Wrappers.<SysMessage>lambdaUpdate()
                .eq(SysMessage::getReceiverId, userId)
                .eq(SysMessage::getReadFlag, 0));
    }

    private MessageVO toVO(SysMessage message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setMsgType(message.getMsgType());
        vo.setBizType(message.getBizType());
        vo.setBizId(message.getBizId());
        vo.setReadFlag(message.getReadFlag());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
