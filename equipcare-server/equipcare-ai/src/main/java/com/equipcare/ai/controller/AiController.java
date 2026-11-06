package com.equipcare.ai.controller;

import com.equipcare.ai.client.OpenAiCompatClient;
import com.equipcare.ai.service.AiDefectService;
import com.equipcare.ai.service.AiDiagnosisService;
import com.equipcare.ai.service.AiDispatchService;
import com.equipcare.ai.service.AiReportService;
import com.equipcare.ai.service.AiStreamGuard;
import com.equipcare.common.annotation.AuditLog;
import com.equipcare.common.result.Result;
import com.equipcare.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 智能副驾端点（equipcare.ai.enabled=true 时才装载）。
 *
 * <p>三个能力：
 * <ol>
 *   <li>GET  /api/ai/diagnosis/{id}/stream —— 故障诊断，SSE 流式（事件：delta / ai.error / done）；</li>
 *   <li>POST /api/ai/dispatch-suggest/{id} —— 智能派单推荐（算法预打分 + LLM 理由，模型失败降级纯算法）；</li>
 *   <li>POST /api/ai/report/{id} —— 维修报告生成（Markdown）。</li>
 * </ol>
 * 权限复用现有工单权限码（不新增 sys_permission）；全部挂审计注解。
 */
@Tag(name = "AI 副驾", description = "故障诊断 / 智能派单 / 维修报告（OpenAI 兼容多 Provider）")
@RestController
@RequestMapping("/api/ai")
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiDiagnosisService diagnosisService;
    private final AiDispatchService dispatchService;
    private final AiReportService reportService;
    private final AiDefectService defectService;
    private final AiStreamGuard streamGuard;

    public AiController(AiDiagnosisService diagnosisService, AiDispatchService dispatchService,
                        AiReportService reportService, AiDefectService defectService,
                        AiStreamGuard streamGuard) {
        this.diagnosisService = diagnosisService;
        this.dispatchService = dispatchService;
        this.reportService = reportService;
        this.defectService = defectService;
        this.streamGuard = streamGuard;
    }

    @Operation(summary = "AI 故障诊断（SSE 流式输出 Markdown）")
    @GetMapping(value = "/diagnosis/{workOrderId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter diagnosis(@PathVariable long workOrderId) {
        Long userId = SecurityUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean finished = new AtomicBoolean(false);
        // 上游句柄：回调注册在启动 worker 之前，断开瞬间即可取消，无竞态窗口
        AtomicReference<OpenAiCompatClient.StreamHandle> handleRef = new AtomicReference<>();

        if (!streamGuard.tryAcquire(userId)) {
            return reject(emitter, "您已有一个诊断任务进行中，或系统繁忙，请稍后再试");
        }
        AtomicBoolean guardReleased = new AtomicBoolean(false);
        Runnable releaseOnce = () -> {
            if (guardReleased.compareAndSet(false, true)) {
                streamGuard.release(userId);
            }
        };
        // 统一生命周期钩子：断开 / 超时 / 完成 → 取消上游（停烧 token）+ 释放并发名额
        Runnable cleanup = () -> {
            finished.set(true);
            OpenAiCompatClient.StreamHandle h = handleRef.get();
            if (h != null) {
                h.cancel();
            }
            releaseOnce.run();
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(t -> cleanup.run());

        Thread worker = new Thread(() -> {
            try {
                OpenAiCompatClient.StreamHandle handle = diagnosisService.diagnose(workOrderId,
                        new AiDiagnosisService.Callbacks(
                                delta -> {
                                    if (!finished.get()) {
                                        try {
                                            emitter.send(SseEmitter.event().name("delta").data(delta));
                                        } catch (Exception e) {
                                            // 客户端断开：结束本端并取消上游
                                            finished.set(true);
                                            emitter.complete();
                                        }
                                    }
                                },
                                error -> {
                                    if (!finished.get()) {
                                        try {
                                            emitter.send(SseEmitter.event().name("ai.error")
                                                    .data(Map.of("message", error)));
                                        } catch (Exception ignored) {
                                            // 客户端已断开
                                        }
                                    }
                                },
                                () -> {
                                    if (!finished.get()) {
                                        try {
                                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                                        } catch (Exception ignored) {
                                            // 客户端已断开
                                        }
                                        emitter.complete();
                                    }
                                }));
                handleRef.set(handle);
            } catch (Exception e) {
                log.error("AI 诊断上下文构建失败 workOrderId={}", workOrderId, e);
                try {
                    emitter.send(SseEmitter.event().name("ai.error")
                            .data(Map.of("message", e.getMessage() == null ? "诊断失败" : e.getMessage())));
                } catch (Exception ignored) {
                    // 客户端已断开
                }
                emitter.complete();
                releaseOnce.run();
            }
        }, "ai-diagnosis-" + workOrderId);
        worker.setDaemon(true);
        worker.start();
        return emitter;
    }

    @Operation(summary = "AI 智能派单推荐 TOP3")
    @PostMapping("/dispatch-suggest/{workOrderId}")
    @PreAuthorize("hasAuthority('wo:workorder:dispatch')")
    @AuditLog(module = "AI副驾", action = "智能派单推荐")
    public Result<List<AiDispatchService.Suggestion>> dispatchSuggest(@PathVariable long workOrderId) {
        return Result.ok(dispatchService.suggest(workOrderId));
    }

    @Operation(summary = "AI 生成维修报告（Markdown）")
    @PostMapping("/report/{workOrderId}")
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    @AuditLog(module = "AI副驾", action = "维修报告生成")
    public Result<Map<String, String>> report(@PathVariable long workOrderId) {
        return Result.ok(Map.of("markdown", reportService.generate(workOrderId)));
    }

    @Operation(summary = "巡检/报修拍照缺陷识别（多模态视觉）")
    @PostMapping("/defect-analyze")
    @PreAuthorize("hasAuthority('wo:workorder:view')")
    @AuditLog(module = "AI副驾", action = "缺陷识别")
    public Result<Map<String, String>> defectAnalyze(@RequestBody DefectAnalyzeRequest req) {
        return Result.ok(Map.of("markdown", defectService.analyze(req.fileIds(), req.hint())));
    }

    /** 缺陷识别请求：现场照片附件 id（最多 4 张）+ 文字线索 */
    public record DefectAnalyzeRequest(List<Long> fileIds, String hint) {
    }

    /** 并发被拒：以 SSE 错误事件返回并立即完成 */
    private SseEmitter reject(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("ai.error").data(Map.of("message", message)));
            emitter.complete();
        } catch (Exception ignored) {
            // 客户端可能已断开
        }
        return emitter;
    }
}
