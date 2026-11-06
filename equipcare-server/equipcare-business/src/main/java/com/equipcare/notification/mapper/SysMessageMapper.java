package com.equipcare.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.notification.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {

    /**
     * 指定业务对象自某时间点以来已发的消息条数。
     *
     * <p>周期任务（库存预警 / SLA 预警）的去重依据：同一 biz（如同一备件、
     * 同一工单的同一天）只发一次，避免定时任务每轮都生成重复通知。
     */
    @Select("""
            SELECT COUNT(1) FROM sys_message
             WHERE biz_type = #{bizType} AND biz_id = #{bizId}
               AND create_time >= #{since}
            """)
    int countSince(@Param("bizType") String bizType, @Param("bizId") Long bizId,
                   @Param("since") LocalDateTime since);
}
