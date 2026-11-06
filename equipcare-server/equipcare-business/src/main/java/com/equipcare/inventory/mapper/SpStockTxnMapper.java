package com.equipcare.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.inventory.entity.SpStockTxn;
import com.equipcare.inventory.vo.PartCostStatVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SpStockTxnMapper extends BaseMapper<SpStockTxn> {

    @Select("""
            SELECT * FROM sp_stock_txn
             WHERE part_id = #{partId}
             ORDER BY id DESC
             LIMIT #{limit}
            """)
    List<SpStockTxn> selectRecentByPart(@Param("partId") Long partId, @Param("limit") int limit);

    @Select("SELECT MAX(txn_no) FROM sp_stock_txn WHERE txn_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxTxnNoByPrefix(@Param("prefix") String prefix);

    /** 当前读取当日最大流水号（FOR UPDATE）：并发生成流水号时读到最新已提交值，避免重复编号 */
    @Select("SELECT MAX(txn_no) FROM sp_stock_txn WHERE txn_no LIKE CONCAT(#{prefix}, '%') FOR UPDATE")
    String selectMaxTxnNoByPrefixForUpdate(@Param("prefix") String prefix);
    /**
     * 维修备件成本统计：按备件聚合工单领用出库（OUT + WORK_ORDER）的数量与金额。
     * 时间参数为 NULL 时忽略该边界。退料不冲减本统计（口径见 PartCostStatVO 注释）。
     */
    @Select("""
            <script>
            SELECT t.part_id            AS partId,
                   p.part_no            AS partNo,
                   p.part_name          AS partName,
                   p.category           AS category,
                   SUM(ABS(t.quantity)) AS totalQty,
                   SUM(t.amount)        AS totalAmount
              FROM sp_stock_txn t
              JOIN sp_part p ON p.id = t.part_id
             WHERE t.txn_type = 'OUT' AND t.biz_type = 'WORK_ORDER'
             <if test="timeFrom != null"> AND t.txn_time &gt;= #{timeFrom}</if>
             <if test="timeTo != null">   AND t.txn_time &lt;= #{timeTo}</if>
             GROUP BY t.part_id, p.part_no, p.part_name, p.category
             ORDER BY totalAmount DESC
            </script>
            """)
    List<PartCostStatVO> selectWorkOrderCostStats(@Param("timeFrom") LocalDateTime timeFrom,
                                                  @Param("timeTo") LocalDateTime timeTo);
}
