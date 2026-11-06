package com.equipcare.inspection.support;

import com.equipcare.inspection.enums.InspectionCycleType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 巡检周期配置的解析与判定。
 *
 * <p>把 {@code insp_plan.cycle_config} 这段 JSON 收敛成一个不可变对象，
 * 让「某个日期是否该执行」「当天几点执行」变成可单测的纯函数，
 * 定时器只负责遍历日期，不掺杂解析逻辑。
 *
 * <p>容错原则：配置损坏时退化为每天 09:00，而不是抛异常。原因是定时器按计划逐个处理，
 * 单个计划的坏配置不应该让整个调度链路停摆。
 */
public final class CycleConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalTime DEFAULT_TIME = LocalTime.of(9, 0);

    private final LocalTime time;
    private final Set<Integer> weekDays;
    private final Set<Integer> monthDays;

    private CycleConfig(LocalTime time, Set<Integer> weekDays, Set<Integer> monthDays) {
        this.time = time;
        this.weekDays = weekDays;
        this.monthDays = monthDays;
    }

    public static CycleConfig parse(String json) {
        LocalTime time = DEFAULT_TIME;
        Set<Integer> weekDays = new LinkedHashSet<>();
        Set<Integer> monthDays = new LinkedHashSet<>();
        if (json != null && !json.isBlank()) {
            try {
                JsonNode node = MAPPER.readTree(json);
                String rawTime = node.path("time").asText(null);
                if (rawTime != null && !rawTime.isBlank()) {
                    try {
                        time = LocalTime.parse(rawTime.trim());
                    } catch (RuntimeException ignored) {
                        // 非法时间按默认 09:00
                    }
                }
                readInts(node.get("weekDays"), weekDays);
                // 兼容历史数据：seed 中月计划用的是 days
                readInts(node.get("days"), monthDays);
                readInts(node.get("monthDays"), monthDays);
            } catch (Exception ignored) {
                // 配置损坏：退化为每天 09:00
            }
        }
        return new CycleConfig(time, weekDays, monthDays);
    }

    public static String toJson(String time, List<Integer> weekDays, List<Integer> monthDays) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("time", time == null || time.isBlank() ? DEFAULT_TIME.toString() : time);
        if (weekDays != null && !weekDays.isEmpty()) {
            map.put("weekDays", weekDays);
        }
        if (monthDays != null && !monthDays.isEmpty()) {
            map.put("monthDays", monthDays);
        }
        try {
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"time\":\"" + map.get("time") + "\"}";
        }
    }

    private static void readInts(JsonNode node, Set<Integer> target) {
        if (node == null || !node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item.canConvertToInt()) {
                int value = item.asInt();
                if (value >= 1 && value <= 31) {
                    target.add(value);
                }
            }
        }
    }

    /**
     * 判断某个自然日是否落在周期内。
     *
     * <p>MONTHLY 对 29/30/31 日做月末收敛：配置 31 日时，2 月会在 28/29 日执行。
     */
    public boolean matches(LocalDate date, InspectionCycleType type) {
        if (date == null || type == null) {
            return false;
        }
        switch (type) {
            case DAILY:
                return true;
            case WEEKLY: {
                Set<Integer> days = weekDays.isEmpty() ? Set.of(1) : weekDays;
                return days.contains(date.getDayOfWeek().getValue());
            }
            case MONTHLY: {
                Set<Integer> days = monthDays.isEmpty() ? Set.of(1) : monthDays;
                int length = date.lengthOfMonth();
                for (Integer day : days) {
                    if (day == null) {
                        continue;
                    }
                    int effective = Math.min(Math.max(day, 1), length);
                    if (effective == date.getDayOfMonth()) {
                        return true;
                    }
                }
                return false;
            }
            default:
                return false;
        }
    }

    public LocalDateTime occurrenceOn(LocalDate date) {
        return date.atTime(time);
    }

    public LocalTime getTime() {
        return time;
    }

    public Set<Integer> getWeekDays() {
        return weekDays;
    }

    public Set<Integer> getMonthDays() {
        return monthDays;
    }

    /** 人类可读的周期描述，用于列表展示 */
    public String describe(InspectionCycleType type) {
        if (type == null) {
            return "";
        }
        String hhmm = time.toString();
        switch (type) {
            case WEEKLY: {
                Set<Integer> days = weekDays.isEmpty() ? Set.of(1) : weekDays;
                StringBuilder sb = new StringBuilder("每周 ");
                String[] names = {"", "一", "二", "三", "四", "五", "六", "日"};
                int index = 0;
                for (Integer day : days) {
                    if (index++ > 0) {
                        sb.append("、");
                    }
                    sb.append(day >= 1 && day <= 7 ? names[day] : String.valueOf(day));
                }
                return sb.append(" ").append(hhmm).toString();
            }
            case MONTHLY: {
                Set<Integer> days = monthDays.isEmpty() ? Set.of(1) : monthDays;
                StringBuilder sb = new StringBuilder("每月 ");
                int index = 0;
                for (Integer day : days) {
                    if (index++ > 0) {
                        sb.append("、");
                    }
                    sb.append(day);
                }
                return sb.append(" 日 ").append(hhmm).toString();
            }
            case DAILY:
            default:
                return "每日 " + hhmm;
        }
    }
}
