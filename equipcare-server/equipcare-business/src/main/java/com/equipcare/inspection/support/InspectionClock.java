package com.equipcare.inspection.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 巡检业务时钟。
 *
 * <p>巡检「哪一天该执行」完全取决于自然日，时区错了会导致跨时区部署时任务整体偏移一天，
 * 所以这里把时区收敛到一个配置项（默认 Asia/Shanghai），所有日期计算都从这里取 now，
 * 不直接调用 {@code LocalDate.now()}。
 *
 * <p>同时它让定时逻辑可测：单测可以直接构造固定时刻调用生成服务，而不必等时钟。
 */
@Component
public class InspectionClock {

    private final ZoneId zone;

    public InspectionClock(@Value("${equipcare.inspection.zone:Asia/Shanghai}") String zone) {
        this.zone = ZoneId.of(zone);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zone);
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public ZoneId zone() {
        return zone;
    }
}
