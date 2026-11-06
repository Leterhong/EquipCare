package com.equipcare.asset.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.asset.dto.EquipmentFileBindDTO;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.entity.EqEquipmentFile;
import com.equipcare.asset.enums.EquipmentFileType;
import com.equipcare.asset.mapper.EqEquipmentFileMapper;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.asset.service.EquipmentFileService;
import com.equipcare.asset.vo.EquipmentFileVO;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.result.ResultCode;
import com.equipcare.common.security.SecurityUtils;
import com.equipcare.system.entity.SysFile;
import com.equipcare.system.mapper.SysFileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备图片与附件实现。
 *
 * <p>设计取舍：文件先由统一上传接口落库（有扩展名白名单、大小限制、MD5 去重），
 * 设备侧只保存关联关系。这样同一张图片可被多处引用，删除设备也不会误删物理文件。
 */
@Service
public class EquipmentFileServiceImpl implements EquipmentFileService {

    private static final Logger log = LoggerFactory.getLogger(EquipmentFileServiceImpl.class);

    private final EqEquipmentFileMapper equipmentFileMapper;
    private final EqEquipmentMapper equipmentMapper;
    private final SysFileMapper fileMapper;

    public EquipmentFileServiceImpl(EqEquipmentFileMapper equipmentFileMapper,
                                    EqEquipmentMapper equipmentMapper,
                                    SysFileMapper fileMapper) {
        this.equipmentFileMapper = equipmentFileMapper;
        this.equipmentMapper = equipmentMapper;
        this.fileMapper = fileMapper;
    }

    @Override
    public List<EquipmentFileVO> list(Long equipmentId) {
        requireEquipment(equipmentId);
        return equipmentFileMapper.selectFilesByEquipment(equipmentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EquipmentFileVO bind(Long equipmentId, EquipmentFileBindDTO dto) {
        requireEquipment(equipmentId);
        SysFile file = fileMapper.selectById(dto.getFileId());
        if (file == null) {
            throw BizException.notFound("文件");
        }
        String fileType = dto.getFileType() == null ? EquipmentFileType.ATTACHMENT.getCode() : dto.getFileType();
        if (!EquipmentFileType.isValid(fileType)) {
            throw new BizException(ResultCode.VALIDATION_FAILED, "文件用途只能是 IMAGE 或 ATTACHMENT");
        }
        // 图片必须是图片，避免把 pdf 当封面
        if (EquipmentFileType.IMAGE.getCode().equals(fileType) && !isImage(file.getContentType())) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "只有图片文件可以作为设备图片");
        }
        if (equipmentFileMapper.countBinding(equipmentId, dto.getFileId()) > 0) {
            log.debug("文件已绑定，幂等返回 equipmentId={} fileId={}", equipmentId, dto.getFileId());
            return findVo(equipmentId, dto.getFileId());
        }

        EqEquipmentFile relation = new EqEquipmentFile();
        relation.setEquipmentId(equipmentId);
        relation.setFileId(dto.getFileId());
        relation.setFileType(fileType);
        relation.setSortNo(dto.getSortNo() == null ? nextSortNo(equipmentId, fileType) : dto.getSortNo());
        relation.setCreateBy(SecurityUtils.getCurrentUserId());
        equipmentFileMapper.insert(relation);
        log.info("设备文件绑定 equipmentId={} fileId={} type={}", equipmentId, dto.getFileId(), fileType);
        return findVo(equipmentId, dto.getFileId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long equipmentId, Long fileId) {
        requireEquipment(equipmentId);
        int rows = equipmentFileMapper.delete(Wrappers.<EqEquipmentFile>lambdaQuery()
                .eq(EqEquipmentFile::getEquipmentId, equipmentId)
                .eq(EqEquipmentFile::getFileId, fileId));
        if (rows == 0) {
            throw BizException.notFound("设备文件");
        }
        log.info("设备文件解绑 equipmentId={} fileId={}", equipmentId, fileId);
    }

    @Override
    public String coverUrl(Long equipmentId) {
        return equipmentFileMapper.selectCoverUrl(equipmentId);
    }

    private void requireEquipment(Long equipmentId) {
        EqEquipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            throw BizException.notFound("设备");
        }
    }

    private EquipmentFileVO findVo(Long equipmentId, Long fileId) {
        return equipmentFileMapper.selectFilesByEquipment(equipmentId).stream()
                .filter(item -> fileId.equals(item.getFileId()))
                .findFirst()
                .orElseThrow(() -> BizException.notFound("设备文件"));
    }

    private int nextSortNo(Long equipmentId, String fileType) {
        long count = equipmentFileMapper.selectCount(Wrappers.<EqEquipmentFile>lambdaQuery()
                .eq(EqEquipmentFile::getEquipmentId, equipmentId)
                .eq(EqEquipmentFile::getFileType, fileType));
        return (int) count + 1;
    }

    private boolean isImage(String contentType) {
        return contentType != null && contentType.toLowerCase().startsWith("image/");
    }
}
