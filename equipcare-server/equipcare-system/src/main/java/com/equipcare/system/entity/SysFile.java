package com.equipcare.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 附件元数据。
 */
@Data
@TableName("sys_file")
public class SysFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    private String storedName;

    private String filePath;

    private String fileUrl;

    private Long fileSize;

    private String contentType;

    private String md5;

    private String bizType;

    private Long bizId;

    private Long uploadUserId;

    private LocalDateTime createTime;
}
