package com.equipcare.asset.service;

import com.equipcare.asset.dto.LocationSaveDTO;
import com.equipcare.asset.vo.LocationTreeVO;

import java.util.List;

/**
 * 位置服务。
 */
public interface LocationService {

    /**
     * 位置树，含每个位置下的设备数量。
     */
    List<LocationTreeVO> tree();

    /**
     * 新增或更新位置。full_path 由父级路径自动拼接维护。
     *
     * @return 位置ID
     */
    Long save(LocationSaveDTO dto);

    /**
     * 删除位置。存在子位置或已被设备引用时拒绝删除。
     */
    void delete(Long id);
}
