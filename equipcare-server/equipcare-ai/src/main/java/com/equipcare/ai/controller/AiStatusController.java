package com.equipcare.ai.controller;

import com.equipcare.ai.config.AiProperties;
import com.equipcare.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 能力状态探针（常驻端点，无论 AI 是否启用都返回明确状态）。
 * 前端登录后调用一次，决定是否渲染 AI 入口。
 */
@Tag(name = "AI 副驾 · 状态")
@RestController
@RequestMapping("/api/ai")
public class AiStatusController {

    private final AiProperties props;

    public AiStatusController(AiProperties props) {
        this.props = props;
    }

    @Operation(summary = "AI 能力状态（enabled=false 时前端隐藏 AI 入口）")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(Map.of(
                "enabled", props.isEnabled(),
                "model", props.isEnabled() ? props.getModel() : ""));
    }
}
