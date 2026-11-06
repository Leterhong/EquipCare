package com.equipcare.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.inventory.entity.SpPart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SpPartMapper extends BaseMapper<SpPart> {

    /**
     * 原子扣减库存。影响行数为 0 表示库存不足（并发下也不会出现负库存）。
     */
    @Update("""
            UPDATE sp_part
               SET current_stock = current_stock - #{qty},
                   version = version + 1,
                   update_time = NOW()
             WHERE id = #{partId} AND deleted = 0 AND status = 1 AND current_stock >= #{qty}
            """)
    int deductStock(@Param("partId") Long partId, @Param("qty") int qty);

    /** 入库 / 退料回增 */
    @Update("""
            UPDATE sp_part
               SET current_stock = current_stock + #{qty},
                   version = version + 1,
                   update_time = NOW()
             WHERE id = #{partId} AND deleted = 0
            """)
    int increaseStock(@Param("partId") Long partId, @Param("qty") int qty);

    /** 低于安全库存的备件，用于库存预警 */
    @Select("""
            SELECT * FROM sp_part
             WHERE deleted = 0 AND status = 1 AND current_stock < safety_stock
             ORDER BY (safety_stock - current_stock) DESC
            """)
    List<SpPart> selectBelowSafetyStock();

    /** 备件编码是否已存在（含已删除，编码不复用） */
    @Select("SELECT COUNT(1) FROM sp_part WHERE part_no = #{partNo}")
    int countByPartNoIncludeDeleted(@Param("partNo") String partNo);
}
