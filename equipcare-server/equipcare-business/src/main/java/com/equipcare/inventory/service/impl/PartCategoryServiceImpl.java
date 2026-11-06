package com.equipcare.inventory.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.common.exception.BizException;
import com.equipcare.inventory.dto.CategorySaveDTO;
import com.equipcare.inventory.entity.SpPart;
import com.equipcare.inventory.entity.SpPartCategory;
import com.equipcare.inventory.mapper.SpPartCategoryMapper;
import com.equipcare.inventory.mapper.SpPartMapper;
import com.equipcare.inventory.service.PartCategoryService;
import com.equipcare.inventory.vo.PartCategoryVO;
import com.equipcare.common.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 备件分类实现。
 *
 * <p>约束：分类名唯一（含已删除，名称不复用）；分类下仍有备件时禁止删除；
 * 重命名必须同步 {@code sp_part.category} 快照，否则旧快照会让按分类过滤漏数据。
 */
@Service
public class PartCategoryServiceImpl implements PartCategoryService {

    private final SpPartCategoryMapper categoryMapper;
    private final SpPartMapper partMapper;

    public PartCategoryServiceImpl(SpPartCategoryMapper categoryMapper, SpPartMapper partMapper) {
        this.categoryMapper = categoryMapper;
        this.partMapper = partMapper;
    }

    @Override
    public List<PartCategoryVO> listAll() {
        List<SpPartCategory> categories = categoryMapper.selectList(
                Wrappers.<SpPartCategory>lambdaQuery()
                        .orderByAsc(SpPartCategory::getSort)
                        .orderByAsc(SpPartCategory::getCategoryName));
        List<PartCategoryVO> list = new ArrayList<>(categories.size());
        for (SpPartCategory category : categories) {
            PartCategoryVO vo = new PartCategoryVO();
            vo.setId(category.getId());
            vo.setCategoryName(category.getCategoryName());
            vo.setRemark(category.getRemark());
            vo.setSort(category.getSort());
            vo.setPartCount(partMapper.selectCount(Wrappers.<SpPart>lambdaQuery()
                    .eq(SpPart::getCategoryId, category.getId())));
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(CategorySaveDTO dto) {
        String name = dto.getCategoryName().trim();
        if (dto.getId() == null) {
            if (categoryMapper.countByNameIncludeDeleted(name) > 0) {
                throw BizException.duplicate("备件分类「" + name + "」");
            }
            SpPartCategory entity = new SpPartCategory();
            entity.setCategoryName(name);
            entity.setRemark(dto.getRemark());
            entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
            entity.setCreateBy(SecurityUtils.getCurrentUserIdOrNull());
            categoryMapper.insert(entity);
            return entity.getId();
        }
        SpPartCategory existing = categoryMapper.selectById(dto.getId());
        if (existing == null) {
            throw BizException.notFound("备件分类");
        }
        if (!existing.getCategoryName().equals(name)
                && categoryMapper.countByNameIncludeDeleted(name) > 0) {
            throw BizException.duplicate("备件分类「" + name + "」");
        }
        SpPartCategory update = new SpPartCategory();
        update.setId(dto.getId());
        update.setCategoryName(name);
        update.setRemark(dto.getRemark());
        update.setSort(dto.getSort() == null ? 0 : dto.getSort());
        update.setUpdateBy(SecurityUtils.getCurrentUserIdOrNull());
        categoryMapper.updateById(update);
        // 名称变了就同步快照，保证备件列表的按分类过滤继续命中
        if (!existing.getCategoryName().equals(name)) {
            categoryMapper.syncPartCategoryName(dto.getId(), name);
        }
        return dto.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SpPartCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw BizException.notFound("备件分类");
        }
        long partCount = categoryMapper.countParts(id);
        if (partCount > 0) {
            throw BizException.inUse("分类下仍有 " + partCount + " 个备件，请先转移再删除");
        }
        categoryMapper.deleteById(id);
    }
}
