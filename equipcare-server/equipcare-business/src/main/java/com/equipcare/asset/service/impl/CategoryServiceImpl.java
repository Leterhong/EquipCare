package com.equipcare.asset.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.asset.dto.CategorySaveDTO;
import com.equipcare.asset.entity.EqCategory;
import com.equipcare.asset.mapper.EqCategoryMapper;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.asset.service.CategoryService;
import com.equipcare.asset.vo.CategoryTreeVO;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.query.GroupCount;
import com.equipcare.common.result.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备类别实现。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    private final EqCategoryMapper categoryMapper;
    private final EqEquipmentMapper equipmentMapper;

    public CategoryServiceImpl(EqCategoryMapper categoryMapper, EqEquipmentMapper equipmentMapper) {
        this.categoryMapper = categoryMapper;
        this.equipmentMapper = equipmentMapper;
    }

    @Override
    public List<CategoryTreeVO> tree() {
        List<EqCategory> categories = categoryMapper.selectList(Wrappers.<EqCategory>lambdaQuery()
                .orderByAsc(EqCategory::getSort)
                .orderByAsc(EqCategory::getId));
        Map<Long, Long> counts = new HashMap<>();
        for (GroupCount gc : equipmentMapper.countGroupByCategory()) {
            if (gc.getId() != null) {
                counts.put(gc.getId(), gc.getTotal());
            }
        }

        Map<Long, CategoryTreeVO> byId = new LinkedHashMap<>();
        for (EqCategory c : categories) {
            CategoryTreeVO node = new CategoryTreeVO();
            node.setId(c.getId());
            node.setParentId(c.getParentId() == null ? 0L : c.getParentId());
            node.setCategoryCode(c.getCategoryCode());
            node.setCategoryName(c.getCategoryName());
            node.setDefaultTemplateId(c.getDefaultTemplateId());
            node.setMaintainCycleDays(c.getMaintainCycleDays());
            node.setSort(c.getSort());
            node.setStatus(c.getStatus());
            node.setEquipmentCount(counts.getOrDefault(c.getId(), 0L).intValue());
            byId.put(c.getId(), node);
        }

        List<CategoryTreeVO> roots = new ArrayList<>();
        for (CategoryTreeVO node : byId.values()) {
            CategoryTreeVO parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            if (parent != null && !parent.getId().equals(node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(CategorySaveDTO dto) {
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        if (parentId != 0L) {
            EqCategory parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw BizException.notFound("父级类别");
            }
        }

        // 类别编码全局唯一，且作为资产编号前缀，不允许重复
        Long duplicated = categoryMapper.selectCount(Wrappers.<EqCategory>lambdaQuery()
                .eq(EqCategory::getCategoryCode, dto.getCategoryCode())
                .ne(dto.getId() != null, EqCategory::getId, dto.getId()));
        if (duplicated != null && duplicated > 0) {
            throw BizException.duplicate("类别编码 " + dto.getCategoryCode());
        }

        EqCategory entity = new EqCategory();
        entity.setParentId(parentId);
        entity.setCategoryCode(dto.getCategoryCode());
        entity.setCategoryName(dto.getCategoryName());
        entity.setDefaultTemplateId(dto.getDefaultTemplateId());
        entity.setMaintainCycleDays(dto.getMaintainCycleDays());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());

        if (dto.getId() == null) {
            categoryMapper.insert(entity);
            return entity.getId();
        }

        EqCategory existing = categoryMapper.selectById(dto.getId());
        if (existing == null) {
            throw BizException.notFound("设备类别");
        }
        if (dto.getId().equals(parentId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "父级类别不能是自己");
        }
        // 防止把父节点挂到自己的子孙节点下形成环
        if (parentId != 0L && isDescendant(dto.getId(), parentId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "父级类别不能是当前类别的子孙节点");
        }
        entity.setId(dto.getId());
        categoryMapper.updateById(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EqCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("设备类别");
        }
        Long children = categoryMapper.selectCount(Wrappers.<EqCategory>lambdaQuery()
                .eq(EqCategory::getParentId, id));
        if (children != null && children > 0) {
            throw BizException.inUse("该类别下存在子类别，请先删除子类别");
        }
        int used = equipmentMapper.countByCategory(id);
        if (used > 0) {
            throw BizException.inUse("该类别下存在 " + used + " 台设备，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    /**
     * 判断 candidateParentId 是否为 id 的子孙节点。
     */
    private boolean isDescendant(Long id, Long candidateParentId) {
        Long cursor = candidateParentId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 32) {
            if (cursor.equals(id)) {
                return true;
            }
            EqCategory node = categoryMapper.selectById(cursor);
            if (node == null) {
                return false;
            }
            cursor = node.getParentId();
        }
        return false;
    }
}
