package com.equipcare.realtime;

import com.equipcare.common.realtime.SseHub;
import com.equipcare.common.result.Result;
import com.equipcare.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * SSE 实时推送端点。
 *
 * <p>两段式鉴权：浏览器 EventSource 不能带 Authorization 头，
 * 先 {@code POST /api/sse/ticket}（JWT 保护）换一次性票据，
 * 再 {@code GET /api/sse/stream?ticket=} 建立长连接（票据即凭证，白名单放行）。
 */
@Tag(name = "实时推送", description = "SSE 长连接：消息铃铛 / 工单看板 / SLA 与库存预警实时刷新")
@RestController
@RequestMapping("/api/sse")
@ConditionalOnProperty(name = "equipcare.sse.enabled", havingValue = "true", matchIfMissing = true)
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final SseTicketService ticketService;
    private final SseHub sseHub;

    public SseController(SseTicketService ticketService, SseHub sseHub) {
        this.ticketService = ticketService;
        this.sseHub = sseHub;
    }

    @Operation(summary = "签发 SSE 连接票据（一次性，30 秒有效）")
    @PostMapping("/ticket")
    public Result<Map<String, String>> ticket() {
        Long userId = SecurityUtils.getCurrentUserId();
        String ticket = ticketService.issue(userId);
        return Result.ok(Map.of("ticket", ticket));
    }

    @Operation(summary = "建立 SSE 长连接（票据鉴权）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("ticket") String ticket) {
        // EventSource 不带 Authorization 头：连接身份由一次性票据携带（签发时经 JWT 鉴权）
        Long userId = ticketService.resolve(ticket);
        if (userId == null) {
            // Spring 会把 SseEmitter 的立即完成表现为连接关闭；错误信息靠事件体传递
            SseEmitter rejected = new SseEmitter(0L);
            try {
                rejected.send(SseEmitter.event().name("connect:error")
                        .data(Map.of("message", "票据无效或已过期，请重新登录")));
                rejected.complete();
            } catch (Exception ignored) {
                // 客户端可能已断开
            }
            return rejected;
        }
        SseEmitter emitter = sseHub.register(userId);
        try {
            // 连接建立即推一条握手事件，前端以此确认订阅成功
            emitter.send(SseEmitter.event().name("connected")
                    .data(Map.of("message", "实时通道已建立", "online", sseHub.onlineCount())));
        } catch (Exception e) {
            log.warn("SSE 握手事件发送失败 userId={}", userId);
        }
        return emitter;
    }
}
