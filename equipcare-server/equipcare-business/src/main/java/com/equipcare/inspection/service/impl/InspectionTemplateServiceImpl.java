package com.equipcare.inspection.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.equipcare.common.enums.EnumCode;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.PageResult;
import com.equipcare.common.result.ResultCode;
import com.equipcare.inspection.dto.TemplateItemDTO;
import com.equipcare.inspection.dto.TemplateQuery;
import com.equipcare.inspection.dto.TemplateSaveDTO;
import com.equipcare.inspection.entity.InspTemplate;
import com.equipcare.inspection.entity.InspTemplateItem;
import com.equipcare.inspection.enums.AnomalyLevel;
import com.equipcare.inspection.enums.InspItemType;
import com.equipcare.inspection.enums.TemplateStatus;
import com.equipcare.inspection.mapper.InspTemplateItemMapper;
import com.equipcare.inspection.mapper.InspTemplateMapper;
import com.equipcare.inspection.service.InspectionTemplateService;
import com.equipcare.inspection.support.InspectionClock;
import com.equipcare.inspection.vo.TemplateDetailVO;
import com.equipcare.inspection.vo.TemplateItemVO;
import com.equipcare.inspection.vo.TemplateOptionVO;
import com.equipcare.inspection.vo.TemplateVO;
import com.equipcare.common.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 巡检模板管理实现。
 *
 * <p>三条纪律：
 * <ol>
 *   <li>检查项整组替换，seq_no 由提交顺序重新生成，避免唯一索引 (template_id, seq_no) 冲突；</li>
 *   <li>删除模板只做逻辑删除，不动检查项——历史任务详情仍要能显示当年的检查标准；</li>
 *   <li>发布前做「可判定性」校验：数值项必须有区间、枚举项必须有选项，避免执行页出现无法判定的项。</li>
 * </ol>
 */
@Service
public class InspectionTemplateServiceImpl implements InspectionTemplateService {

    private static final Logger log = LoggerFactory.getLogger(InspectionTemplateServiceImpl.class);

    private static final String CODE_PREFIX = "TPL";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final InspTemplateMapper templateMapper;
    private final InspTemplateItemMapper itemMapper;
    private final InspectionClock clock;

    public InspectionTemplateServiceImpl(InspTemplateMapper templateMapper, InspTemplateItemMapper itemMapper,
                                         InspectionClock clock) {
        this.templateMapper = templateMapper;
        this.itemMapper = itemMapper;
        this.clock = clock;
    }

    @Override
    public PageResult<TemplateVO> page(TemplateQuery query) {
        IPage<TemplateVO> page = templateMapper.selectTemplatePage(
                new Page<>(query.getPageNum(), query.getPageSize()), query);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public TemplateDetailVO detail(Long id) {
        TemplateVO base = templateMapper.selectTemplateDetail(id);
        if (base == null) {
            throw BizException.notFound("巡检模板");
        }
        TemplateDetailVO vo = new TemplateDetailVO();
        vo.setId(base.getId());
        vo.setTemplateCode(base.getTemplateCode());
        vo.setTemplateName(base.getTemplateName());
        vo.setCategoryId(base.getCategoryId());
        vo.setCategoryName(base.getCategoryName());
        vo.setVersion(base.getVersion());
        vo.setStatus(base.getStatus());
        vo.setItemCount(base.getItemCount());
        vo.setDescription(base.getDescription());
        vo.setPublishTime(base.getPublishTime());
        vo.setUpdateTime(base.getUpdateTime());
        vo.setPlanCount(base.getPlanCount());
        for (InspTemplateItem item : itemMapper.selectByTemplate(id)) {
            TemplateItemVO iv = new TemplateItemVO();
            iv.setId(item.getId());
            iv.setSeqNo(item.getSeqNo());
            iv.setItemName(item.getItemName());
            iv.setItemType(item.getItemType());
            iv.setUnit(item.getUnit());
            iv.setMinValue(item.getMinValue());
            iv.setMaxValue(item.getMaxValue());
            iv.setNormalValue(item.getNormalValue());
            iv.setEnumOptions(item.getEnumOptions());
            iv.setRequired(item.getRequired());
            iv.setCheckMethod(item.getCheckMethod());
            iv.setStandardRef(item.getStandardRef());
            iv.setAbnormalRule(item.getAbnormalRule());
            iv.setAutoWorkOrder(item.getAutoWorkOrder());
            iv.setPhotoRequired(item.getPhotoRequired());
            iv.setAbnormalLevel(item.getAbnormalLevel());
            iv.setUpdateTime(item.getUpdateTime());
            vo.getItems().add(iv);
        }
        return vo;
    }

    @Override
    public List<TemplateOptionVO> options() {
        return templateMapper.selectOptions();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TemplateSaveDTO dto) {
        List<TemplateItemDTO> items = dto.getItems() == null ? List.of() : dto.getItems();
        validateItems(items);

        String code = StringUtils.hasText(dto.getTemplateCode())
                ? dto.getTemplateCode().trim() : nextCode();
        if (templateMapper.countByCode(code, 0L) > 0) {
            throw BizException.duplicate("模板编码 " + code);
        }

        InspTemplate template = new InspTemplate();
        template.setTemplateCode(code);
        template.setTemplateName(dto.getTemplateName().trim());
        template.setCategoryId(dto.getCategoryId());
        template.setDescription(dto.getDescription());
        template.setVersion(1);
        template.setStatus(TemplateStatus.DRAFT.getCode());
        template.setItemCount(items.size());
        templateMapper.insert(template);

        saveItems(template.getId(), items);
        log.info("创建巡检模板 code={} 检查项={} 个", code, items.size());
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TemplateSaveDTO dto) {
        InspTemplate template = requireTemplate(id);
        List<TemplateItemDTO> items = dto.getItems() == null ? List.of() : dto.getItems();
        validateItems(items);

        if (StringUtils.hasText(dto.getTemplateCode())) {
            String code = dto.getTemplateCode().trim();
            if (!code.equals(template.getTemplateCode()) && templateMapper.countByCode(code, id) > 0) {
                throw BizException.duplicate("模板编码 " + code);
            }
            template.setTemplateCode(code);
        }
        template.setTemplateName(dto.getTemplateName().trim());
        template.setCategoryId(dto.getCategoryId());
        template.setDescription(dto.getDescription());
        template.setItemCount(items.size());
        // 已发布模板的改动要留痕：版本号 +1，历史任务里的项名称与标准是快照，不受影响
        if (TemplateStatus.PUBLISHED.getCode().equals(template.getStatus())) {
            template.setVersion((template.getVersion() == null ? 1 : template.getVersion()) + 1);
        }
        templateMapper.updateById(template);

        // 整组替换检查项：先删后建，避免 diff 造成 seq 冲突
        itemMapper.delete(Wrappers.<InspTemplateItem>lambdaQuery().eq(InspTemplateItem::getTemplateId, id));
        saveItems(id, items);
        log.info("更新巡检模板 id={} 检查项={} 个 版本={}", id, items.size(), template.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTemplate(id);
        int plans = templateMapper.countPlansUsing(id);
        if (plans > 0) {
            throw BizException.inUse("该模板已被 " + plans + " 个巡检计划引用，无法删除；可先停用模板或调整计划");
        }
        templateMapper.deleteById(id);
        log.info("逻辑删除巡检模板 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        InspTemplate template = requireTemplate(id);
        List<InspTemplateItem> items = itemMapper.selectByTemplate(id);
        if (items.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "模板至少需要一个检查项才能发布");
        }
        template.setStatus(TemplateStatus.PUBLISHED.getCode());
        template.setItemCount(items.size());
        template.setPublishTime(clock.now());
        template.setPublisherId(SecurityUtils.getCurrentUserId());
        templateMapper.updateById(template);
        log.info("发布巡检模板 id={} code={}", id, template.getTemplateCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        InspTemplate template = requireTemplate(id);
        template.setStatus(TemplateStatus.DISABLED.getCode());
        templateMapper.updateById(template);
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    private InspTemplate requireTemplate(Long id) {
        InspTemplate template = id == null ? null : templateMapper.selectById(id);
        if (template == null) {
            throw BizException.notFound("巡检模板");
        }
        return template;
    }

    /**
     * 检查项可判定性校验。
     *
     * <p>这一步拦住的都是「能存进去但执行时无法判定」的配置，
     * 比如数值项没给区间、枚举项没给选项——真到现场才发现，巡检人只能瞎填。
     */
    private void validateItems(List<TemplateItemDTO> items) {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            TemplateItemDTO item = items.get(i);
            int no = i + 1;
            if (!names.add(item.getItemName().trim())) {
                throw new BizException(ResultCode.BAD_REQUEST, "第 " + no + " 个检查项名称重复：" + item.getItemName());
            }
            InspItemType type = InspItemType.of(item.getItemType());
            if (type == null) {
                throw new BizException(ResultCode.BAD_REQUEST, "第 " + no + " 个检查项类型不支持");
            }
            item.setItemType(type.getCode());

            switch (type) {
                case NUMBER:
                    validateNumberRange(item, no);
                    break;
                case ENUM:
                    validateEnumOptions(item, no);
                    break;
                case BOOLEAN:
                    if (!StringUtils.hasText(item.getNormalValue())) {
                        item.setNormalValue("1");
                    }
                    if (!"0".equals(item.getNormalValue()) && !"1".equals(item.getNormalValue())) {
                        throw new BizException(ResultCode.BAD_REQUEST,
                                "第 " + no + " 个检查项的正常值只能是 0 或 1");
                    }
                    break;
                case TEXT:
                default:
                    break;
            }

            if (item.getRequired() == null) {
                item.setRequired(1);
            }
            if (item.getAutoWorkOrder() == null) {
                item.setAutoWorkOrder(0);
            }
            if (item.getPhotoRequired() == null) {
                item.setPhotoRequired(0);
            }
            if (!StringUtils.hasText(item.getAbnormalLevel())) {
                item.setAbnormalLevel(AnomalyLevel.MEDIUM.getCode());
            } else if (!EnumCode.contains(AnomalyLevel.class, item.getAbnormalLevel())) {
                throw new BizException(ResultCode.BAD_REQUEST, "第 " + no + " 个检查项的异常等级取值非法");
            }
        }
    }

    private void validateNumberRange(TemplateItemDTO item, int no) {
        BigDecimal min = item.getMinValue();
        BigDecimal max = item.getMaxValue();
        if (min == null && max == null && !StringUtils.hasText(item.getNormalValue())) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "第 " + no + " 个数值型检查项需要设置上下限或正常值，否则无法判定异常");
        }
        if (min != null && max != null && min.compareTo(max) >= 0) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "第 " + no + " 个检查项的数值下限必须小于上限");
        }
    }

    private void validateEnumOptions(TemplateItemDTO item, int no) {
        String raw = item.getEnumOptions();
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ResultCode.BAD_REQUEST, "第 " + no + " 个枚举型检查项需要配置选项");
        }
        try {
            JsonNode node = JSON.readTree(raw);
            if (!node.isArray() || node.size() < 2) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "第 " + no + " 个检查项的枚举选项至少需要 2 个");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "第 " + no + " 个检查项的枚举选项格式应为 JSON 数组，如 [\"正常\",\"异常\"]");
        }
    }

    private void saveItems(Long templateId, List<TemplateItemDTO> items) {
        for (int i = 0; i < items.size(); i++) {
            TemplateItemDTO dto = items.get(i);
            InspTemplateItem entity = new InspTemplateItem();
            entity.setTemplateId(templateId);
            // 排序以提交顺序为准，避免前端传重号撞唯一索引
            entity.setSeqNo(i + 1);
            entity.setItemName(dto.getItemName().trim());
            entity.setItemType(dto.getItemType());
            entity.setUnit(dto.getUnit());
            entity.setMinValue(dto.getMinValue());
            entity.setMaxValue(dto.getMaxValue());
            entity.setNormalValue(dto.getNormalValue());
            entity.setEnumOptions(dto.getEnumOptions());
            entity.setRequired(dto.getRequired());
            entity.setCheckMethod(dto.getCheckMethod());
            entity.setStandardRef(dto.getStandardRef());
            entity.setAbnormalRule(dto.getAbnormalRule());
            entity.setAutoWorkOrder(dto.getAutoWorkOrder());
            entity.setPhotoRequired(dto.getPhotoRequired());
            entity.setAbnormalLevel(dto.getAbnormalLevel());
            itemMapper.insert(entity);
        }
    }

    private String nextCode() {
        String prefix = CODE_PREFIX + clock.today().format(DAY);
        String max = templateMapper.selectMaxCodeByPrefix(prefix);
        int next = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                next = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
        }
        return prefix + String.format("%03d", next);
    }
}
