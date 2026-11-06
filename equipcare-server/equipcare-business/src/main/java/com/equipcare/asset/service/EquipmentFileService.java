package com.equipcare.asset.service;

import com.equipcare.asset.dto.EquipmentFileBindDTO;
import com.equipcare.asset.vo.EquipmentFileVO;

import java.util.List;

/**
 * 设备图片与附件。
 */
public interface EquipmentFileService {

    List<EquipmentFileVO> list(Long equipmentId);

    /** 绑定文件；同一文件重复绑定直接幂等返回 */
    EquipmentFileVO bind(Long equipmentId, EquipmentFileBindDTO dto);

    void unbind(Long equipmentId, Long fileId);

    /** 设备封面地址，无图片返回 null */
    String coverUrl(Long equipmentId);
}
