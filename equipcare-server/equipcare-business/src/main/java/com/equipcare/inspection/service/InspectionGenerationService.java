package com.equipcare.inspection.service;

import com.equipcare.inspection.entity.InspPlan;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 巡检任务自动生成。
 *
 * <p>设计要点（对应验收要求中的六类问题）：
 * <ul>
 *   <li><b>重复生成</b>：唯一索引 (plan_id, equipment_id, plan_date) + INSERT IGNORE，双层拦截；</li>
 *   <li><b>时区</b>：所有日期基于 {@link com.equipcare.inspection.support.InspectionClock} 的配置时区，不读 JVM 默认时区；</li>
 *   <li><b>定时任务异常</b>：按计划逐个 try/catch，单计划失败不影响其他计划，异常计入汇总；</li>
 *   <li><b>计划暂停</b>：只扫描 status=ENABLED 的计划；</li>
 *   <li><b>任务过期</b>：由 {@code InspectionTaskService#expireOverdue} 单独处理，与生成解耦；</li>
 *   <li><b>重启恢复</b>：不依赖内存水位，每次按 [now - 回补天数, now + 提前小时数] 扫库，
 *       重启后自然补齐停机期间漏生成的日期。</li>
 * </ul>
 */
public interface InspectionGenerationService {

    /** 扫描全部启用中的计划（定时器入口） */
    Summary generateDue(LocalDateTime now);

    /** 单个计划生成；force=true 时把窗口延伸到「下一次执行时刻」，供「立即生成」按钮使用 */
    Summary generateForPlan(Long planId, LocalDateTime now, boolean force);

    /** 同步作用域设备快照（新增的补进来，移出范围的删掉） */
    int syncScopeEquipment(InspPlan plan);

    /** 按作用域解析设备ID（分类/位置会展开子级） */
    List<Long> resolveScopeEquipmentIds(String scopeType, String scopeConfig);

    /** 下一次执行时刻；已超出计划结束日期时返回 null */
    LocalDateTime nextOccurrence(InspPlan plan, LocalDateTime now);

    /**
     * 生成结果汇总。
     *
     * @param plans      扫描的计划数
     * @param created    实际创建的任务数
     * @param duplicate  因已存在而跳过的任务数
     * @param noExecutor 因无法确定执行人而跳过的任务数
     * @param errors     生成失败的计划数
     */
    record Summary(int plans, int created, int duplicate, int noExecutor, int errors) {

        public static Summary empty() {
            return new Summary(0, 0, 0, 0, 0);
        }

        public Summary plus(Summary other) {
            return new Summary(plans + other.plans, created + other.created,
                    duplicate + other.duplicate, noExecutor + other.noExecutor, errors + other.errors);
        }
    }
}
