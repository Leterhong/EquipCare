package com.equipcare.asset.service;

import com.equipcare.asset.dto.CategorySaveDTO;
import com.equipcare.asset.vo.CategoryTreeVO;

import java.util.List;

/**
 * 设备类别服务。
 */
public interface CategoryService {

    /**
     * 类别树，含每个类别下的设备数量。
     */
    List<CategoryTreeVO> tree();

    /**
     * 新增或更新类别。
     *
     * @return 类别ID
     */
    Long save(CategorySaveDTO dto);

    /**
     * 删除类别。存在子类别或已被设备引用时拒绝删除。
     */
    void delete(Long id);
}
