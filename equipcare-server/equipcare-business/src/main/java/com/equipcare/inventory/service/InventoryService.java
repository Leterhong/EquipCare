package com.equipcare.inventory.service;

import com.equipcare.common.query.PageQuery;
import com.equipcare.common.result.PageResult;
import com.equipcare.inventory.dto.PartApplyDTO;
import com.equipcare.inventory.dto.PartQuery;
import com.equipcare.inventory.dto.PartSaveDTO;
import com.equipcare.inventory.dto.StockChangeDTO;
import com.equipcare.inventory.dto.StockTxnQuery;
import com.equipcare.inventory.vo.PartApplyVO;
import com.equipcare.inventory.vo.PartCostStatVO;
import com.equipcare.inventory.vo.PartVO;
import com.equipcare.inventory.vo.StockTxnVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 备件与库存服务。
 *
 * <p>核心约束：
 * <ul>
 *   <li>库存数量只能通过入库 / 出库 / 退料 / 盘点调整变动，不存在「直接改库存」的接口；</li>
 *   <li>每次变动都写一条 {@code sp_stock_txn}，记录变动前后库存，可完整重放追溯；</li>
 *   <li>出库使用 {@code UPDATE ... WHERE current_stock >= qty} 原子扣减，并发下不会出现负库存。</li>
 * </ul>
 */
public interface InventoryService {

    /** 备件分页 */
    PageResult<PartVO> pagePart(PartQuery query);

    /** 低于安全库存的备件 */
    List<PartVO> belowSafetyStock();

    /** 新增 / 编辑备件档案 */
    Long savePart(PartSaveDTO dto);

    /** 删除备件档案（存在未完结领用时拒绝） */
    void deletePart(Long partId);

    /** 入库 */
    StockTxnVO stockIn(StockChangeDTO dto);

    /** 直接出库（非工单场景，例如外借） */
    StockTxnVO stockOut(StockChangeDTO dto);

    /** 盘点调整：positive=true 盘盈回增，false 盘亏扣减 */
    StockTxnVO adjust(Long partId, int quantity, boolean positive, String remark);

    /** 工单备件申请（APPLIED，不扣库存） */
    Long applyPart(PartApplyDTO dto);

    /** 审批通过并出库：原子扣减 + 写流水 + 回写工单成本 */
    Long issuePart(Long applyId);

    /** 驳回申请 */
    void rejectApply(Long applyId, String remark);

    /** 退料入库 */
    Long returnPart(Long applyId, String remark);

    /** 库存流水查询（按备件） */
    List<StockTxnVO> recentTxns(Long partId, int limit);

    /** 库存流水分页（按备件 / 类型 / 业务类型 / 时间段过滤，库存流水页使用） */
    PageResult<StockTxnVO> pageTxns(StockTxnQuery query);

    /** 维修备件成本统计：按备件聚合工单领用出库的数量与金额 */
    List<PartCostStatVO> costStats(LocalDateTime timeFrom, LocalDateTime timeTo);

    /** 领用申请分页（审批视角，可按状态 / 工单过滤） */
    PageResult<PartApplyVO> pageApplies(String status, Long workOrderId, PageQuery query);
}
