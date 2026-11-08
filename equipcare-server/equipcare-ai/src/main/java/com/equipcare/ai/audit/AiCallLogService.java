package com.equipcare.ai.audit;

import com.equipcare.ai.config.AiProperties;
import com.equipcare.ai.mapper.AiCallLogMapper;
import com.equipcare.common.security.LoginUser;
import com.equipcare.common.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * AI 调用审计落库（只插入，渗透整改建议项）。
 *
 * <p>四类调用（诊断/报告/派单/缺陷识别）完成后各记录一行：谁在何时对哪张工单
 * 用了什么模型、结果与耗时。落库失败只记日志——审计不可用不得影响业务主流程。
 * 行级租户过滤由 TenantLineInnerInterceptor 在 INSERT 时自动补 tenant_id。
 */
@Service
@ConditionalOnProperty(name = "equipcare.ai.enabled", havingValue = "true")
public class AiCallLogService {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogService.class);
    private static final int MAX_ERROR_CHARS = 500;

    private final AiCallLogMapper callLogMapper;
    private final AiProperties props;

    public AiCallLogService(AiCallLogMapper callLogMapper, AiProperties props) {
        this.callLogMapper = callLogMapper;
        this.props = props;
    }

    /**
     * 记录一次 AI 调用（调用方在拿到最终结果后调用；发起人取当前登录上下文）。
     *
     * @param callType   DIAGNOSIS/REPORT/DISPATCH/DEFECT
     * @param bizId      关联工单 id（可空能力除外）
     * @param durationMs 从请求进入到结果确定的耗时
     * @param status     SUCCESS/ERROR
     * @param errorMsg   失败原因（自动截断）
     */
    public void record(String callType, Long bizId, long durationMs, String status, String errorMsg) {
        try {
            LoginUser user = SecurityUtils.getLoginUserOrNull();
            AiCallLog row = new AiCallLog();
            row.setUserId(user == null ? null : user.getUserId());
            row.setUsername(user == null ? "system" : user.getUsername());
            row.setCallType(callType);
            row.setBizId(bizId);
            row.setModel(props.getModel() == null || props.getModel().isBlank()
                    ? props.getVisionModel() : props.getModel());
            row.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
            row.setStatus(status);
            row.setErrorMsg(errorMsg == null ? null
                    : errorMsg.length() > MAX_ERROR_CHARS ? errorMsg.substring(0, MAX_ERROR_CHARS) : errorMsg);
            row.setCreatedAt(LocalDateTime.now());
            callLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("AI 调用审计落库失败 type={} bizId={}", callType, bizId, e);
        }
    }
}
