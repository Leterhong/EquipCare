package com.equipcare.system.service;

import com.equipcare.system.entity.SysFile;
import com.equipcare.system.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地文件存储服务。
 */
public interface FileStorageService {

    /**
     * 保存上传文件并写入元数据。
     *
     * @param file    上传文件
     * @param bizType 业务类型，如 WORK_ORDER
     * @param bizId   业务主键，可为空（先上传后关联）
     */
    FileVO store(MultipartFile file, String bizType, Long bizId);

    /** 按ID查询元数据，不存在时抛业务异常 */
    SysFile getById(Long id);
}
