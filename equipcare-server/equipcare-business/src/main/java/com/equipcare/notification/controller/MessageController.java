package com.equipcare.notification.controller;

import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.Result;
import com.equipcare.notification.dto.MessageQuery;
import com.equipcare.notification.service.MessageService;
import com.equipcare.notification.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内消息。
 */
@Tag(name = "03. 站内消息", description = "我的消息、未读数与已读标记")
@RestController
@RequestMapping("/api/system/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @Operation(summary = "我的消息分页")
    @GetMapping
    public Result<PageResult<MessageVO>> page(@Valid MessageQuery query) {
        return Result.ok(messageService.pageMine(query));
    }

    @Operation(summary = "我的未读消息数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(messageService.unreadCount());
    }

    @Operation(summary = "标记单条已读")
    @PatchMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.ok();
    }

    @Operation(summary = "批量标记已读")
    @PatchMapping("/read-batch")
    public Result<Void> markReadBatch(@RequestBody List<Long> ids) {
        messageService.markReadBatch(ids);
        return Result.ok();
    }

    @Operation(summary = "全部标记已读")
    @PatchMapping("/read-all")
    public Result<Void> markAllRead() {
        messageService.markAllRead();
        return Result.ok();
    }
}
