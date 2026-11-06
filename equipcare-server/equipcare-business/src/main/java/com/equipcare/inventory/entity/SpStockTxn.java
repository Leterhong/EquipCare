package com.equipcare.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水。只允许插入，不可修改与删除；错误通过反向流水冲正。
 *
 * <p>每条流水都记录 {@code before_stock} 与 {@code after_stock}，
 * 保证任意时点的库存都能被重放校验。
 */
@Data
@TableName("sp_stock_txn")
public class SpStockTxn {

    @TableId
    private Long id;

    private String txnNo;

    private Long partId;

    /** IN / OUT / RETURN / ADJUST */
    private String txnType;

    /** WORK_ORDER / PURCHASE / RETURN / CHECK */
    private String bizType;

    private Long bizId;

    private Integer quantity;

    private Integer beforeStock;

    private Integer afterStock;

    private BigDecimal unitPrice;

    private BigDecimal amount;

    private Long operatorId;

    private LocalDateTime txnTime;

    private String remark;
}
