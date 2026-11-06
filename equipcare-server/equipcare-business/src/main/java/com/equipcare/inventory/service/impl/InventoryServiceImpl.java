package com.equipcare.inventory.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.query.PageQuery;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.ResultCode;
import com.equipcare.inventory.dto.PartApplyDTO;
import com.equipcare.inventory.dto.PartQuery;
import com.equipcare.inventory.dto.PartSaveDTO;
import com.equipcare.inventory.dto.StockChangeDTO;
import com.equipcare.inventory.dto.StockTxnQuery;
import com.equipcare.inventory.entity.SpPart;
import com.equipcare.inventory.entity.SpPartCategory;
import com.equipcare.inventory.entity.SpStockTxn;
import com.equipcare.inventory.entity.SpWorkOrderPart;
import com.equipcare.inventory.enums.IssueStatus;
import com.equipcare.inventory.enums.StockTxnType;
import com.equipcare.inventory.mapper.SpPartCategoryMapper;
import com.equipcare.inventory.mapper.SpPartMapper;
import com.equipcare.inventory.mapper.SpStockTxnMapper;
import com.equipcare.inventory.mapper.SpWorkOrderPartMapper;
import com.equipcare.inventory.service.InventoryService;
import com.equipcare.inventory.vo.PartApplyVO;
import com.equipcare.inventory.vo.PartCostStatVO;
import com.equipcare.inventory.vo.PartVO;
import com.equipcare.inventory.vo.StockTxnVO;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.workorder.entity.WoWorkOrder;
import com.equipcare.workorder.enums.WorkOrderStatus;
import com.equipcare.workorder.mapper.WoWorkOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 备件与库存实现。
 *
 * <p>关键设计：
 * <ul>
 *   <li>出库先做原子扣减，影响行数为 0 直接抛 {@code STOCK_INSUFFICIENT}(4001)，事务回滚；</li>
 *   <li>流水的 before/after 通过在扣减前后读取库存得到，同一事务内保证一致；</li>
 *   <li>工单成本只在 ISSUED 时累加，驳回 / 未出库不影响成本。</li>
 * </ul>
 */
@Service
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String BIZ_WORK_ORDER = "WORK_ORDER";

    private final SpPartMapper partMapper;
    private final SpStockTxnMapper txnMapper;
    private final SpWorkOrderPartMapper workOrderPartMapper;
    private final WoWorkOrderMapper workOrderMapper;
    private final SpPartCategoryMapper categoryMapper;

    public InventoryServiceImpl(SpPartMapper partMapper, SpStockTxnMapper txnMapper,
                                SpWorkOrderPartMapper workOrderPartMapper, WoWorkOrderMapper workOrderMapper,
                                SpPartCategoryMapper categoryMapper) {
        this.partMapper = partMapper;
        this.txnMapper = txnMapper;
        this.workOrderPartMapper = workOrderPartMapper;
        this.workOrderMapper = workOrderMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public PageResult<PartVO> pagePart(PartQuery query) {
        IPage<SpPart> page = partMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<SpPart>lambdaQuery()
                        .and(StringUtils.hasText(query.getKeyword()), w -> w
                                .like(SpPart::getPartNo, query.getKeyword()).or()
                                .like(SpPart::getPartName, query.getKeyword()).or()
                                .like(SpPart::getSpec, query.getKeyword()))
                        .eq(query.getCategoryId() != null, SpPart::getCategoryId, query.getCategoryId())
                        .eq(StringUtils.hasText(query.getCategory()), SpPart::getCategory, query.getCategory())
                        .eq(query.getStatus() != null, SpPart::getStatus, query.getStatus())
                        .apply(Boolean.TRUE.equals(query.getBelowSafety()), "current_stock < safety_stock")
                        .orderByAsc(SpPart::getPartNo));
        List<PartVO> records = new ArrayList<>();
        for (SpPart part : page.getRecords()) {
            records.add(toVO(part));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PartVO> belowSafetyStock() {
        List<PartVO> list = new ArrayList<>();
        for (SpPart part : partMapper.selectBelowSafetyStock()) {
            list.add(toVO(part));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePart(PartSaveDTO dto) {
        if (dto.getId() == null) {
            if (partMapper.countByPartNoIncludeDeleted(dto.getPartNo()) > 0) {
                throw BizException.duplicate("备件编码 " + dto.getPartNo());
            }
            SpPart entity = new SpPart();
            applyPartFields(entity, dto);
            entity.setCurrentStock(0);
            partMapper.insert(entity);
            return entity.getId();
        }
        SpPart existing = partMapper.selectById(dto.getId());
        if (existing == null) {
            throw BizException.notFound("备件");
        }
        if (!existing.getPartNo().equals(dto.getPartNo())
                && partMapper.countByPartNoIncludeDeleted(dto.getPartNo()) > 0) {
            throw BizException.duplicate("备件编码 " + dto.getPartNo());
        }
        SpPart update = new SpPart();
        update.setId(dto.getId());
        applyPartFields(update, dto);
        // 库存数量不通过档案编辑修改
        update.setCurrentStock(null);
        partMapper.updateById(update);
        return dto.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePart(Long partId) {
        SpPart part = requirePart(partId);
        Long applied = workOrderPartMapper.selectCount(Wrappers.<SpWorkOrderPart>lambdaQuery()
                .eq(SpWorkOrderPart::getPartId, partId)
                .eq(SpWorkOrderPart::getIssueStatus, IssueStatus.APPLIED.getCode()));
        if (applied != null && applied > 0) {
            throw BizException.inUse("该备件存在待审批的领用申请，无法删除");
        }
        if (part.getCurrentStock() != null && part.getCurrentStock() > 0) {
            throw BizException.inUse("该备件仍有库存 " + part.getCurrentStock() + "，请先处理库存");
        }
        partMapper.deleteById(partId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockTxnVO stockIn(StockChangeDTO dto) {
        SpPart part = requirePart(dto.getPartId());
        int before = nvl(part.getCurrentStock());
        int affected = partMapper.increaseStock(dto.getPartId(), dto.getQuantity());
        if (affected == 0) {
            throw BizException.notFound("备件");
        }
        int after = before + dto.getQuantity();
        SpStockTxn txn = writeTxn(part, StockTxnType.IN, "PURCHASE", null,
                dto.getQuantity(), before, after, dto.getUnitPrice(), dto.getRemark());
        return toTxnVO(txn, part.getPartName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockTxnVO stockOut(StockChangeDTO dto) {
        SpPart part = requirePart(dto.getPartId());
        int before = nvl(part.getCurrentStock());
        int affected = partMapper.deductStock(dto.getPartId(), dto.getQuantity());
        if (affected == 0) {
            throw new BizException(ResultCode.STOCK_INSUFFICIENT,
                    "备件「" + part.getPartName() + "」库存不足，当前可用 " + before);
        }
        int after = before - dto.getQuantity();
        SpStockTxn txn = writeTxn(part, StockTxnType.OUT, "OTHER", null,
                dto.getQuantity(), before, after, dto.getUnitPrice(), dto.getRemark());
        return toTxnVO(txn, part.getPartName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockTxnVO adjust(Long partId, int quantity, boolean positive, String remark) {
        // 纵深防御：数量必须为正整数。负数 + positive 组合会绕过 deductStock 的防负库存闸，
        // 造成负库存与流水失真（阶段十二整改 Q-02）。
        if (quantity <= 0) {
            throw new BizException(ResultCode.BAD_REQUEST, "调整数量必须为正整数");
        }
        SpPart part = requirePart(partId);
        int before = nvl(part.getCurrentStock());
        int after;
        if (positive) {
            partMapper.increaseStock(partId, quantity);
            after = before + quantity;
        } else {
            if (partMapper.deductStock(partId, quantity) == 0) {
                throw new BizException(ResultCode.STOCK_INSUFFICIENT,
                        "盘亏数量超过当前库存，当前可用 " + before);
            }
            after = before - quantity;
        }
        SpStockTxn txn = writeTxn(part, StockTxnType.ADJUST, "CHECK", null,
                positive ? quantity : -quantity, before, after, part.getUnitPrice(), remark);
        return toTxnVO(txn, part.getPartName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long applyPart(PartApplyDTO dto) {
        WoWorkOrder order = workOrderMapper.selectById(dto.getWorkOrderId());
        if (order == null) {
            throw BizException.notFound("工单");
        }
        if (WorkOrderStatus.COMPLETED.getCode().equals(order.getStatus())
                || WorkOrderStatus.CANCELLED.getCode().equals(order.getStatus())) {
            throw BizException.forbidden("工单已结束，不能再申请备件");
        }
        SpPart part = requirePart(dto.getPartId());
        if (part.getStatus() == null || part.getStatus() != 1) {
            throw BizException.forbidden("该备件已停用，无法申领");
        }
        SpWorkOrderPart entity = new SpWorkOrderPart();
        entity.setWorkOrderId(dto.getWorkOrderId());
        entity.setPartId(part.getId());
        entity.setPartName(part.getPartName());
        entity.setQuantity(dto.getQuantity());
        entity.setUnitPrice(part.getUnitPrice());
        entity.setAmount(part.getUnitPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
        entity.setApplyUserId(SecurityUtils.getCurrentUserId());
        entity.setApplyTime(LocalDateTime.now());
        entity.setIssueStatus(IssueStatus.APPLIED.getCode());
        entity.setRemark(dto.getRemark());
        workOrderPartMapper.insert(entity);
        log.info("备件申请 workOrderId={} partId={} qty={}", dto.getWorkOrderId(),
                part.getId(), dto.getQuantity());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long issuePart(Long applyId) {
        // 原子状态迁移：并发重复审批时仅一个请求成功，其余被拒（Phase 11 批七）
        if (workOrderPartMapper.transitionStatus(applyId,
                IssueStatus.APPLIED.getCode(), IssueStatus.ISSUED.getCode()) == 0) {
            SpWorkOrderPart current = workOrderPartMapper.selectById(applyId);
            throw BizException.forbidden(current == null ? "备件申请单不存在"
                    : "备件申请单当前状态为「" + current.getIssueStatus() + "」，不允许该操作");
        }
        SpWorkOrderPart apply = workOrderPartMapper.selectById(applyId);
        SpPart part = requirePart(apply.getPartId());
        int before = nvl(part.getCurrentStock());
        // 原子扣减：并发下也不会超发
        if (partMapper.deductStock(part.getId(), apply.getQuantity()) == 0) {
            throw new BizException(ResultCode.STOCK_INSUFFICIENT,
                    "备件「" + part.getPartName() + "」库存不足，当前可用 " + before);
        }
        int after = before - apply.getQuantity();
        SpStockTxn txn = writeTxn(part, StockTxnType.OUT, BIZ_WORK_ORDER, apply.getWorkOrderId(),
                apply.getQuantity(), before, after, apply.getUnitPrice(), "工单领用出库");

        SpWorkOrderPart patch = new SpWorkOrderPart();
        patch.setId(applyId);
        patch.setAuditUserId(SecurityUtils.getCurrentUserId());
        patch.setAuditTime(java.time.LocalDateTime.now());
        patch.setIssueTime(java.time.LocalDateTime.now());
        patch.setTxnId(txn.getId());
        workOrderPartMapper.updateById(patch);

        // 工单成本原子累加（Phase 11 批八）：不再读取-计算-覆盖，并发出库不丢账
        workOrderMapper.adjustSpareCost(apply.getWorkOrderId(), nvlDecimal(apply.getAmount()));
        log.info("备件出库 applyId={} partId={} qty={} txnNo={}", applyId, part.getId(),
                apply.getQuantity(), txn.getTxnNo());
        return txn.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectApply(Long applyId, String remark) {
        // 原子状态迁移：并发重复审批只成功一次
        if (workOrderPartMapper.transitionStatus(applyId,
                IssueStatus.APPLIED.getCode(), IssueStatus.REJECTED.getCode()) == 0) {
            SpWorkOrderPart current = workOrderPartMapper.selectById(applyId);
            throw BizException.forbidden(current == null ? "备件申请单不存在"
                    : "备件申请单当前状态为「" + current.getIssueStatus() + "」，不允许该操作");
        }
        SpWorkOrderPart patch = new SpWorkOrderPart();
        patch.setId(applyId);
        patch.setAuditUserId(SecurityUtils.getCurrentUserId());
        patch.setAuditTime(LocalDateTime.now());
        patch.setRemark(remark);
        workOrderPartMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long returnPart(Long applyId, String remark) {
        // 原子状态迁移（Phase 11 批七）：ISSUED → RETURNED 单条件更新自带行锁，
        // 并发双退时仅一个请求成功，从根源杜绝重复回增库存 / 重复冲减成本
        if (workOrderPartMapper.transitionStatus(applyId,
                IssueStatus.ISSUED.getCode(), IssueStatus.RETURNED.getCode()) == 0) {
            SpWorkOrderPart current = workOrderPartMapper.selectById(applyId);
            throw BizException.forbidden(current == null ? "备件申请单不存在"
                    : "该领料记录当前状态为「" + (current == null ? "" : current.getIssueStatus())
                    + "」，不能重复退料");
        }
        SpWorkOrderPart apply = workOrderPartMapper.selectById(applyId);
        SpPart part = requirePart(apply.getPartId());
        int before = nvl(part.getCurrentStock());
        partMapper.increaseStock(part.getId(), apply.getQuantity());
        int after = before + apply.getQuantity();
        SpStockTxn txn = writeTxn(part, StockTxnType.RETURN, BIZ_WORK_ORDER, apply.getWorkOrderId(),
                apply.getQuantity(), before, after, apply.getUnitPrice(),
                StringUtils.hasText(remark) ? remark : "工单退料");

        // 状态已由原子迁移更新；备注非空时才补写，避免空 SET 子句的无效 updateById
        if (StringUtils.hasText(remark)) {
            SpWorkOrderPart patch = new SpWorkOrderPart();
            patch.setId(applyId);
            patch.setRemark(remark);
            workOrderPartMapper.updateById(patch);
        }

        // 成本原子冲减（Phase 11 批八）：数据库侧单条 UPDATE，并发不丢账、不为负
        workOrderMapper.adjustSpareCost(apply.getWorkOrderId(), nvlDecimal(apply.getAmount()).negate());
        return txn.getId();
    }

    @Override
    public List<StockTxnVO> recentTxns(Long partId, int limit) {
        int size = limit <= 0 || limit > 200 ? 50 : limit;
        List<StockTxnVO> list = new ArrayList<>();
        SpPart part = partMapper.selectById(partId);
        String partName = part == null ? null : part.getPartName();
        for (SpStockTxn txn : txnMapper.selectRecentByPart(partId, size)) {
            list.add(toTxnVO(txn, partName));
        }
        return list;
    }

    @Override
    public PageResult<StockTxnVO> pageTxns(StockTxnQuery query) {
        IPage<SpStockTxn> page = txnMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<SpStockTxn>lambdaQuery()
                        .eq(query.getPartId() != null, SpStockTxn::getPartId, query.getPartId())
                        .eq(StringUtils.hasText(query.getTxnType()), SpStockTxn::getTxnType, query.getTxnType())
                        .eq(StringUtils.hasText(query.getBizType()), SpStockTxn::getBizType, query.getBizType())
                        .ge(query.getTimeFrom() != null, SpStockTxn::getTxnTime, query.getTimeFrom())
                        .le(query.getTimeTo() != null, SpStockTxn::getTxnTime, query.getTimeTo())
                        .orderByDesc(SpStockTxn::getTxnTime)
                        .orderByDesc(SpStockTxn::getId));
        // 批量补备件名，避免逐行查库
        Set<Long> partIds = new HashSet<>();
        for (SpStockTxn txn : page.getRecords()) {
            partIds.add(txn.getPartId());
        }
        Map<Long, String> names = new HashMap<>();
        if (!partIds.isEmpty()) {
            for (SpPart part : partMapper.selectBatchIds(partIds)) {
                names.put(part.getId(), part.getPartName());
            }
        }
        List<StockTxnVO> records = new ArrayList<>(page.getRecords().size());
        for (SpStockTxn txn : page.getRecords()) {
            records.add(toTxnVO(txn, names.get(txn.getPartId())));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public List<PartCostStatVO> costStats(LocalDateTime timeFrom, LocalDateTime timeTo) {
        return txnMapper.selectWorkOrderCostStats(timeFrom, timeTo);
    }

    @Override
    public PageResult<PartApplyVO> pageApplies(String status, Long workOrderId, PageQuery query) {
        IPage<PartApplyVO> page = workOrderPartMapper.selectApplyPage(
                new Page<>(query.getPageNum(), query.getPageSize()), status, workOrderId);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    private SpStockTxn writeTxn(SpPart part, StockTxnType type, String bizType, Long bizId,
                                int quantity, int before, int after, BigDecimal unitPrice, String remark) {
        BigDecimal price = unitPrice == null ? part.getUnitPrice() : unitPrice;
        SpStockTxn txn = new SpStockTxn();
        txn.setTxnNo(nextTxnNo());
        txn.setPartId(part.getId());
        txn.setTxnType(type.getCode());
        txn.setBizType(bizType);
        txn.setBizId(bizId);
        txn.setQuantity(quantity);
        txn.setBeforeStock(before);
        txn.setAfterStock(after);
        txn.setUnitPrice(price);
        txn.setAmount(price == null ? BigDecimal.ZERO
                : price.multiply(BigDecimal.valueOf(Math.abs(quantity))));
        txn.setOperatorId(SecurityUtils.getCurrentUserIdOrNull());
        txn.setTxnTime(LocalDateTime.now());
        txn.setRemark(remark);
        txnMapper.insert(txn);
        return txn;
    }

    private SpPart requirePart(Long partId) {
        SpPart part = partMapper.selectById(partId);
        if (part == null) {
            throw BizException.notFound("备件");
        }
        return part;
    }

    private SpWorkOrderPart requireApply(Long applyId, IssueStatus expected) {
        SpWorkOrderPart apply = workOrderPartMapper.selectById(applyId);
        if (apply == null) {
            throw BizException.notFound("备件申请单");
        }
        if (!expected.getCode().equals(apply.getIssueStatus())) {
            throw BizException.forbidden("备件申请单当前状态为「" + apply.getIssueStatus()
                    + "」，不允许该操作");
        }
        return apply;
    }

    private String nextTxnNo() {
        String prefix = "ST" + LocalDateTime.now().format(DAY_FORMAT);
        // 当前读（FOR UPDATE）：并发出库 / 退料时读到其他事务刚提交的最大号，防止重复编号（Phase 11 批七/八）
        String max = txnMapper.selectMaxTxnNoByPrefixForUpdate(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                // 编号异常时按 1 重新计数
            }
        }
        return prefix + String.format("%05d", next);
    }

    private void applyPartFields(SpPart entity, PartSaveDTO dto) {
        entity.setPartNo(dto.getPartNo());
        entity.setPartName(dto.getPartName());
        entity.setCategoryId(dto.getCategoryId());
        if (dto.getCategoryId() != null) {
            // 挂了分类实体就以它为准，同步分类快照，保证旧查询条件继续可用
            SpPartCategory category = categoryMapper.selectById(dto.getCategoryId());
            if (category == null) {
                throw BizException.notFound("备件分类");
            }
            entity.setCategory(category.getCategoryName());
        } else {
            entity.setCategory(dto.getCategory());
        }
        entity.setSpec(dto.getSpec());
        entity.setUnit(dto.getUnit());
        entity.setBrand(dto.getBrand());
        entity.setSafetyStock(dto.getSafetyStock() == null ? 0 : dto.getSafetyStock());
        entity.setUnitPrice(dto.getUnitPrice() == null ? BigDecimal.ZERO : dto.getUnitPrice());
        entity.setWarehouseLocation(dto.getWarehouseLocation());
        entity.setSupplier(dto.getSupplier());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }

    private PartVO toVO(SpPart part) {
        PartVO vo = new PartVO();
        vo.setId(part.getId());
        vo.setPartNo(part.getPartNo());
        vo.setPartName(part.getPartName());
        vo.setCategory(part.getCategory());
        vo.setCategoryId(part.getCategoryId());
        vo.setSpec(part.getSpec());
        vo.setUnit(part.getUnit());
        vo.setBrand(part.getBrand());
        vo.setSafetyStock(part.getSafetyStock());
        vo.setCurrentStock(part.getCurrentStock());
        vo.setUnitPrice(part.getUnitPrice());
        vo.setWarehouseLocation(part.getWarehouseLocation());
        vo.setSupplier(part.getSupplier());
        vo.setStatus(part.getStatus());
        vo.setBelowSafety(nvl(part.getCurrentStock()) < nvl(part.getSafetyStock()));
        return vo;
    }

    private StockTxnVO toTxnVO(SpStockTxn txn, String partName) {
        StockTxnVO vo = new StockTxnVO();
        vo.setId(txn.getId());
        vo.setTxnNo(txn.getTxnNo());
        vo.setPartId(txn.getPartId());
        vo.setPartName(partName);
        vo.setTxnType(txn.getTxnType());
        for (StockTxnType type : StockTxnType.values()) {
            if (type.getCode().equals(txn.getTxnType())) {
                vo.setTxnTypeLabel(type.getLabel());
            }
        }
        vo.setBizType(txn.getBizType());
        vo.setBizId(txn.getBizId());
        vo.setQuantity(txn.getQuantity());
        vo.setBeforeStock(txn.getBeforeStock());
        vo.setAfterStock(txn.getAfterStock());
        vo.setUnitPrice(txn.getUnitPrice());
        vo.setAmount(txn.getAmount());
        vo.setOperatorId(txn.getOperatorId());
        vo.setTxnTime(txn.getTxnTime());
        vo.setRemark(txn.getRemark());
        return vo;
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal nvlDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
