package com.equipcare.asset.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.asset.dto.EquipmentQuery;
import com.equipcare.asset.dto.EquipmentSaveDTO;
import com.equipcare.asset.dto.EquipmentStatusDTO;
import com.equipcare.asset.entity.EqCategory;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.entity.EqLocation;
import com.equipcare.asset.enums.EquipmentStatus;
import com.equipcare.asset.mapper.EqCategoryMapper;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.asset.mapper.EqLocationMapper;
import com.equipcare.asset.service.EquipmentFileService;
import com.equipcare.asset.service.EquipmentHistoryService;
import com.equipcare.asset.service.EquipmentService;
import com.equipcare.asset.service.QrCodeService;
import com.equipcare.asset.vo.EquipmentDetailVO;
import com.equipcare.asset.vo.EquipmentScanVO;
import com.equipcare.asset.vo.EquipmentVO;
import com.equipcare.asset.vo.QrCodeVO;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.ResultCode;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.system.entity.SysDept;
import com.equipcare.system.entity.SysUser;
import com.equipcare.system.mapper.SysDeptMapper;
import com.equipcare.system.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 设备台账实现。
 *
 * <p>关键规则：
 * <ul>
 *   <li>资产编号唯一且不可复用（含已删除记录），留空时按「类别编码-年月-4位流水」自动生成；</li>
 *   <li>状态变更独立成接口，维修中状态只能由工单闭环写入，人工只能切 IN_USE / IDLE / SCRAPPED；</li>
 *   <li>报废必须填写原因，报废设备不可再回到在用；</li>
 *   <li>二维码令牌基于 UUID，重新生成后旧码立即失效。</li>
 * </ul>
 */
@Service
public class EquipmentServiceImpl implements EquipmentService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentServiceImpl.class);

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int SEQ_WIDTH = 4;

    private final EqEquipmentMapper equipmentMapper;
    private final EqCategoryMapper categoryMapper;
    private final EqLocationMapper locationMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final EquipmentFileService fileService;
    private final EquipmentHistoryService historyService;
    private final QrCodeService qrCodeService;

    @Value("${equipcare.asset.scan-url-prefix:/m/scan.html}")
    private String scanUrlPrefix;

    /**
     * 扫码地址的对外主机名，例如 https://equip.example.com。
     * 留空则二维码内容为相对地址，仅适合同域调试；生产环境必须配置，
     * 否则手机扫码后无法打开。
     */
    @Value("${equipcare.asset.scan-base-url:}")
    private String scanBaseUrl;

    public EquipmentServiceImpl(EqEquipmentMapper equipmentMapper, EqCategoryMapper categoryMapper,
                               EqLocationMapper locationMapper, SysUserMapper userMapper,
                               SysDeptMapper deptMapper, EquipmentFileService fileService,
                               EquipmentHistoryService historyService, QrCodeService qrCodeService) {
        this.equipmentMapper = equipmentMapper;
        this.categoryMapper = categoryMapper;
        this.locationMapper = locationMapper;
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.fileService = fileService;
        this.historyService = historyService;
        this.qrCodeService = qrCodeService;
    }

    @Override
    public PageResult<EquipmentVO> page(EquipmentQuery query) {
        if (StringUtils.hasText(query.getStatus())
                && !EquipmentStatus.manualChangeable(query.getStatus())
                && !EquipmentStatus.REPAIRING.getCode().equals(query.getStatus())) {
            throw new BizException(ResultCode.BAD_REQUEST, "设备状态查询条件非法");
        }
        IPage<EquipmentVO> page = equipmentMapper.selectEquipmentPage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public EquipmentDetailVO detail(Long id) {
        EqEquipment entity = equipmentMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("设备");
        }
        EquipmentDetailVO vo = new EquipmentDetailVO();
        vo.setId(entity.getId());
        vo.setAssetNo(entity.getAssetNo());
        vo.setEquipmentName(entity.getEquipmentName());
        vo.setCategoryId(entity.getCategoryId());
        vo.setLocationId(entity.getLocationId());
        vo.setDeptId(entity.getDeptId());
        vo.setBrand(entity.getBrand());
        vo.setModel(entity.getModel());
        vo.setSpec(entity.getSpec());
        vo.setSerialNo(entity.getSerialNo());
        vo.setManufacturer(entity.getManufacturer());
        vo.setSupplier(entity.getSupplier());
        vo.setPurchaseDate(entity.getPurchaseDate());
        vo.setInstallDate(entity.getInstallDate());
        vo.setWarrantyEndDate(entity.getWarrantyEndDate());
        vo.setOriginalValue(entity.getOriginalValue());
        vo.setResponsibleUserId(entity.getResponsibleUserId());
        vo.setStatus(entity.getStatus());
        vo.setHealthScore(entity.getHealthScore());
        vo.setLastInspectTime(entity.getLastInspectTime());
        vo.setNextInspectTime(entity.getNextInspectTime());
        vo.setQrToken(entity.getQrToken());
        vo.setRemark(entity.getRemark());
        vo.setVersion(entity.getVersion());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());

        EqCategory category = categoryMapper.selectById(entity.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getCategoryName());
        }
        EqLocation location = locationMapper.selectById(entity.getLocationId());
        if (location != null) {
            vo.setLocationPath(location.getFullPath());
        }
        if (entity.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(entity.getDeptId());
            if (dept != null) {
                vo.setDeptName(dept.getDeptName());
            }
        }
        if (entity.getResponsibleUserId() != null) {
            SysUser user = userMapper.selectById(entity.getResponsibleUserId());
            if (user != null) {
                vo.setResponsibleUserName(user.getRealName());
            }
        }
        // 打印页与详情页需要一次拿到图片、附件与历史，避免前端串联四次请求
        vo.setCoverUrl(fileService.coverUrl(id));
        vo.setFiles(fileService.list(id));
        vo.setRecentInspections(historyService.inspectHistory(id, 10));
        vo.setRepairHistory(historyService.repairHistory(id, 10));
        vo.setRepairCount(historyService.countRepairHistory(id));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(EquipmentSaveDTO dto) {
        EqCategory category = categoryMapper.selectById(dto.getCategoryId());
        if (category == null) {
            throw BizException.notFound("设备类别");
        }
        EqLocation location = locationMapper.selectById(dto.getLocationId());
        if (location == null) {
            throw BizException.notFound("所在位置");
        }
        if (dto.getResponsibleUserId() != null && userMapper.selectById(dto.getResponsibleUserId()) == null) {
            throw BizException.notFound("设备责任人");
        }
        if (dto.getPurchaseDate() != null && dto.getInstallDate() != null
                && dto.getInstallDate().isBefore(dto.getPurchaseDate())) {
            throw new BizException(ResultCode.BAD_REQUEST, "安装日期不能早于采购日期");
        }
        if (dto.getWarrantyEndDate() != null && dto.getPurchaseDate() != null
                && dto.getWarrantyEndDate().isBefore(dto.getPurchaseDate())) {
            throw new BizException(ResultCode.BAD_REQUEST, "保修截止日期不能早于采购日期");
        }

        String assetNo = StringUtils.hasText(dto.getAssetNo()) ? dto.getAssetNo().trim() : null;

        if (dto.getId() == null) {
            if (assetNo == null) {
                assetNo = generateAssetNo(category.getCategoryCode());
            } else if (equipmentMapper.countByAssetNoIncludeDeleted(assetNo) > 0) {
                throw BizException.duplicate("资产编号 " + assetNo);
            }
            EqEquipment entity = new EqEquipment();
            applyFields(entity, dto);
            entity.setAssetNo(assetNo);
            entity.setCategoryId(category.getId());
            entity.setLocationId(location.getId());
            entity.setStatus(EquipmentStatus.IN_USE.getCode());
            entity.setHealthScore(BigDecimal.valueOf(100));
            entity.setQrToken(newQrToken());
            equipmentMapper.insert(entity);
            log.info("设备新增成功 id={} assetNo={}", entity.getId(), entity.getAssetNo());
            return entity.getId();
        }

        EqEquipment existing = equipmentMapper.selectById(dto.getId());
        if (existing == null) {
            throw BizException.notFound("设备");
        }
        if (EquipmentStatus.SCRAPPED.getCode().equals(existing.getStatus())) {
            throw BizException.forbidden("已报废设备不可编辑");
        }
        if (assetNo != null && !assetNo.equals(existing.getAssetNo())) {
            if (equipmentMapper.countByAssetNoIncludeDeleted(assetNo) > 0) {
                throw BizException.duplicate("资产编号 " + assetNo);
            }
            existing.setAssetNo(assetNo);
        }
        applyFields(existing, dto);
        existing.setCategoryId(category.getId());
        existing.setLocationId(location.getId());
        // 状态不在此处修改：状态由 changeStatus / 工单流转单独维护
        equipmentMapper.updateById(existing);
        log.info("设备更新成功 id={} assetNo={}", existing.getId(), existing.getAssetNo());
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, EquipmentStatusDTO dto) {
        EqEquipment existing = equipmentMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("设备");
        }
        String target = dto.getStatus();
        if (!EquipmentStatus.manualChangeable(target)) {
            throw BizException.forbidden("「维修中」状态由工单流转自动维护，不支持手工变更");
        }
        if (target.equals(existing.getStatus())) {
            throw BizException.forbidden("设备已处于该状态，无需变更");
        }
        if (EquipmentStatus.REPAIRING.getCode().equals(existing.getStatus())) {
            throw BizException.forbidden("设备维修中，请先完成或取消关联工单");
        }
        if (EquipmentStatus.SCRAPPED.getCode().equals(target) && !StringUtils.hasText(dto.getReason())) {
            throw new BizException(ResultCode.BAD_REQUEST, "报废设备必须填写原因");
        }
        if (EquipmentStatus.SCRAPPED.getCode().equals(existing.getStatus())
                && !EquipmentStatus.SCRAPPED.getCode().equals(target)) {
            throw BizException.forbidden("已报废设备不可恢复");
        }

        EqEquipment update = new EqEquipment();
        update.setId(id);
        update.setStatus(target);
        update.setVersion(existing.getVersion());
        if (equipmentMapper.updateById(update) == 0) {
            throw new BizException(ResultCode.CONFLICT, "设备状态已被他人修改，请刷新后重试");
        }
        log.info("设备状态变更 id={} {} -> {} 原因={}", id, existing.getStatus(), target, dto.getReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EqEquipment existing = equipmentMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("设备");
        }
        if (EquipmentStatus.REPAIRING.getCode().equals(existing.getStatus())) {
            throw BizException.inUse("设备维修中，存在进行中的工单，无法删除");
        }
        equipmentMapper.deleteById(id);
        log.info("设备删除 id={} assetNo={}", id, existing.getAssetNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QrCodeVO regenerateQr(Long id) {
        EqEquipment existing = equipmentMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("设备");
        }
        String token = newQrToken();
        EqEquipment update = new EqEquipment();
        update.setId(id);
        update.setQrToken(token);
        update.setVersion(existing.getVersion());
        if (equipmentMapper.updateById(update) == 0) {
            throw new BizException(ResultCode.CONFLICT, "设备信息已被他人修改，请刷新后重试");
        }
        log.info("设备二维码重新生成 id={} assetNo={}", id, existing.getAssetNo());
        return buildQr(existing.getId(), existing.getAssetNo(), existing.getEquipmentName(), token);
    }

    @Override
    public QrCodeVO qrCode(Long id) {
        EqEquipment existing = equipmentMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("设备");
        }
        return buildQr(existing.getId(), existing.getAssetNo(), existing.getEquipmentName(), existing.getQrToken());
    }

    @Override
    public EquipmentScanVO scan(String qrToken, String assetNo) {
        if (!StringUtils.hasText(qrToken) && !StringUtils.hasText(assetNo)) {
            throw new BizException(ResultCode.BAD_REQUEST, "请提供二维码令牌或资产编号");
        }
        EqEquipment entity = StringUtils.hasText(qrToken)
                ? equipmentMapper.selectByQrToken(qrToken.trim())
                : equipmentMapper.selectOne(Wrappers.<EqEquipment>lambdaQuery()
                        .eq(EqEquipment::getAssetNo, assetNo.trim()));
        if (entity == null) {
            throw BizException.notFound("设备");
        }

        EquipmentScanVO vo = new EquipmentScanVO();
        vo.setId(entity.getId());
        vo.setAssetNo(entity.getAssetNo());
        vo.setEquipmentName(entity.getEquipmentName());
        vo.setStatus(entity.getStatus());
        vo.setHealthScore(entity.getHealthScore());
        vo.setBrand(entity.getBrand());
        vo.setModel(entity.getModel());
        vo.setResponsibleUserId(entity.getResponsibleUserId());
        vo.setLastInspectTime(entity.getLastInspectTime());
        vo.setNextInspectTime(entity.getNextInspectTime());
        vo.setInspectOverdue(entity.getNextInspectTime() != null
                && entity.getNextInspectTime().isBefore(LocalDateTime.now()));
        vo.setInspectable(!EquipmentStatus.SCRAPPED.getCode().equals(entity.getStatus()));
        vo.setStatusLabel(labelOf(entity.getStatus()));

        EqCategory category = categoryMapper.selectById(entity.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getCategoryName());
        }
        EqLocation location = locationMapper.selectById(entity.getLocationId());
        if (location != null) {
            vo.setLocationPath(location.getFullPath());
        }
        if (entity.getResponsibleUserId() != null) {
            SysUser user = userMapper.selectById(entity.getResponsibleUserId());
            if (user != null) {
                vo.setResponsibleUserName(user.getRealName());
            }
        }
        vo.setCoverUrl(fileService.coverUrl(entity.getId()));
        vo.setRecentInspections(historyService.inspectHistory(entity.getId(), 5));
        vo.setRepairHistory(historyService.repairHistory(entity.getId(), 5));
        vo.setScannedBy(SecurityUtils.getLoginUser().getRealName());
        return vo;
    }

    @Override
    public byte[] qrCodePng(Long id, int size, int margin) {
        QrCodeVO content = qrCode(id);
        return qrCodeService.png(content.getContent(), size, margin);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private void applyFields(EqEquipment entity, EquipmentSaveDTO dto) {
        entity.setEquipmentName(dto.getEquipmentName());
        entity.setDeptId(dto.getDeptId());
        entity.setBrand(dto.getBrand());
        entity.setModel(dto.getModel());
        entity.setSpec(dto.getSpec());
        entity.setSerialNo(dto.getSerialNo());
        entity.setManufacturer(dto.getManufacturer());
        entity.setSupplier(dto.getSupplier());
        entity.setPurchaseDate(dto.getPurchaseDate());
        entity.setInstallDate(dto.getInstallDate());
        entity.setWarrantyEndDate(dto.getWarrantyEndDate());
        entity.setOriginalValue(dto.getOriginalValue());
        entity.setResponsibleUserId(dto.getResponsibleUserId());
        entity.setRemark(dto.getRemark());
    }

    /**
     * 生成资产编号：{类别编码}-{yyyyMM}-{4位流水}。
     * <p>流水号在同一前缀下递增，取最大值 +1 并补零。
     */
    private String generateAssetNo(String categoryCode) {
        String prefix = categoryCode + "-" + LocalDateTime.now().format(MONTH_FORMAT) + "-";
        String max = equipmentMapper.selectMaxAssetNoByPrefix(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            String tail = max.substring(prefix.length());
            try {
                next = Integer.parseInt(tail) + 1;
            } catch (NumberFormatException e) {
                log.warn("资产编号流水解析失败，按 1 重新计数。maxAssetNo={}", max);
            }
        }
        if (next > 9999) {
            throw BizException.forbidden("本月资产编号已用尽，请联系管理员手工指定编号");
        }
        return prefix + String.format("%0" + SEQ_WIDTH + "d", next);
    }

    private String newQrToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private QrCodeVO buildQr(Long id, String assetNo, String equipmentName, String token) {
        // 二维码只编码「扫码地址 + 随机令牌」，不含账号、口令、JWT、手机号等任何敏感信息
        String path = scanUrlPrefix + "?token=" + token;
        String url = StringUtils.hasText(scanBaseUrl)
                ? scanBaseUrl.replaceAll("/+$", "") + path
                : path;
        QrCodeVO vo = new QrCodeVO();
        vo.setEquipmentId(id);
        vo.setAssetNo(assetNo);
        vo.setEquipmentName(equipmentName);
        vo.setQrToken(token);
        vo.setScanUrl(url);
        vo.setContent(url);
        vo.setImageUrl("/api/asset/equipment/" + id + "/qrcode.png");
        return vo;
    }

    private String labelOf(String status) {
        for (EquipmentStatus value : EquipmentStatus.values()) {
            if (value.getCode().equals(status)) {
                return value.getLabel();
            }
        }
        return status;
    }
}
