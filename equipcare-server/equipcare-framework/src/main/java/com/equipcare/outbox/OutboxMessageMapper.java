package com.equipcare.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 事件发件箱 Mapper。
 *
 * <p>所有状态迁移一律走本接口的<b>条件 UPDATE 原子语句</b>，
 * 禁止「先查后改」——多线程 / 未来多实例下只有原子认领才不重不漏。
 */
public interface OutboxMessageMapper extends BaseMapper<OutboxMessage> {

    /**
     * 批量原子认领：只有 PENDING 且到期的事件会被本线程独占。
     * MySQL 单表 UPDATE ... ORDER BY ... LIMIT 保证语句级原子。
     *
     * @return 本线程认领到的事件行数
     */
    @Update("""
            UPDATE event_outbox
            SET status = 'PROCESSING', locked_by = #{worker}, locked_at = NOW(3), attempt = attempt + 1
            WHERE status = 'PENDING' AND next_attempt_at <= NOW(3)
            ORDER BY id
            LIMIT #{batch}
            """)
    int claimBatch(@Param("worker") String worker, @Param("batch") int batch);

    /** 单条原子认领（事务提交后快速投递路径） */
    @Update("""
            UPDATE event_outbox
            SET status = 'PROCESSING', locked_by = #{worker}, locked_at = NOW(3), attempt = attempt + 1
            WHERE id = #{id} AND status = 'PENDING' AND next_attempt_at <= NOW(3)
            """)
    int claimById(@Param("worker") String worker, @Param("id") long id);

    /** 取回本线程本轮认领到的事件（认领与取回共用 worker + 认领时间窗，避免误取他人） */
    @Select("""
            SELECT * FROM event_outbox
            WHERE locked_by = #{worker} AND status = 'PROCESSING' AND locked_at >= #{since}
            ORDER BY id
            """)
    List<OutboxMessage> selectClaimedBy(@Param("worker") String worker, @Param("since") LocalDateTime since);

    /** 投递成功（条件更新：只对本线程认领中的行生效） */
    @Update("""
            UPDATE event_outbox SET status = 'SUCCESS', published_at = NOW(3), locked_by = NULL, locked_at = NULL
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markSuccess(@Param("id") long id);

    /** 投递失败：安排下次重试（指数退避）或判死 */
    @Update("""
            UPDATE event_outbox
            SET status = #{status}, next_attempt_at = #{nextAttemptAt}, locked_by = NULL, locked_at = NULL
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markRetryOrFail(@Param("id") long id, @Param("status") String status,
                        @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    /** 回收僵死认领：PROCESSING 超过 staleSeconds 未结束视为 Worker 崩溃，放回队列 */
    @Update("""
            UPDATE event_outbox SET status = 'PENDING', locked_by = NULL, locked_at = NULL
            WHERE status = 'PROCESSING' AND locked_at < DATE_SUB(NOW(3), INTERVAL #{staleSeconds} SECOND)
            """)
    int reclaimStale(@Param("staleSeconds") int staleSeconds);

    /** 待投递积压数（监控/健康展示用） */
    @Select("SELECT COUNT(*) FROM event_outbox WHERE status = 'PENDING'")
    long countPending();
}
