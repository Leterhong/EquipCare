package com.equipcare.inventory.service;

import com.equipcare.inventory.dto.CategorySaveDTO;
import com.equipcare.inventory.vo.PartCategoryVO;

import java.util.List;

/**
 * 备件分类服务。
 */
public interface PartCategoryService {

    /** 全部分类（按 sort、名称排序），带每类备件数 */
    List<PartCategoryVO> listAll();

    /** 新增 / 编辑分类；重命名时同步备件档案上的分类快照 */
    Long save(CategorySaveDTO dto);

    /** 删除分类：分类下仍有备件时拒绝 */
    void delete(Long id);
}
