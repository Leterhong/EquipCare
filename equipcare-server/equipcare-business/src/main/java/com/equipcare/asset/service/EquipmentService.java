package com.equipcare.asset.service;

import com.equipcare.asset.dto.EquipmentQuery;
import com.equipcare.asset.dto.EquipmentSaveDTO;
import com.equipcare.asset.dto.EquipmentStatusDTO;
import com.equipcare.asset.vo.EquipmentDetailVO;
import com.equipcare.asset.vo.EquipmentScanVO;
import com.equipcare.asset.vo.EquipmentVO;
import com.equipcare.asset.vo.QrCodeVO;
import com.equipcare.common.result.PageResult;

/**
 * 设备台账服务。
 */
public interface EquipmentService {

    /**
     * 设备分页查询。
     */
    PageResult<EquipmentVO> page(EquipmentQuery query);

    /**
     * 设备详情。
     */
    EquipmentDetailVO detail(Long id);

    /**
     * 新增或更新设备。
     * <p>编辑时不允许修改状态，状态必须走 {@link #changeStatus(Long, EquipmentStatusDTO)}
     * 以保证「状态变更」受独立权限与校验约束。
     *
     * @return 设备ID
     */
    Long save(EquipmentSaveDTO dto);

    /**
     * 人工变更设备状态。维修中状态由工单驱动，禁止手工设置。
     */
    void changeStatus(Long id, EquipmentStatusDTO dto);

    /**
     * 逻辑删除设备。
     */
    void delete(Long id);

    /**
     * 重新生成二维码令牌，旧二维码立即失效。
     */
    QrCodeVO regenerateQr(Long id);

    /**
     * 获取二维码内容（不重新生成）。
     */
    QrCodeVO qrCode(Long id);

    /**
     * 扫码查询设备信息。
     *
     * @param qrToken 二维码令牌
     * @param assetNo 资产编号（手工输入场景与令牌二选一）
     */
    EquipmentScanVO scan(String qrToken, String assetNo);

    /**
     * 生成设备二维码 PNG。
     *
     * @param size   边长像素 120-1024
     * @param margin 静区格数 0-8
     */
    byte[] qrCodePng(Long id, int size, int margin);
}
