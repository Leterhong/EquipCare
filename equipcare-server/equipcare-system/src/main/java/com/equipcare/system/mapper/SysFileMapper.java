package com.equipcare.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.system.entity.SysFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysFileMapper extends BaseMapper<SysFile> {

    /** 租户已用存储字节合计（多租户配额用；拦截器自动按租户过滤） */
    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(file_size), 0) FROM sys_file")
    long sumFileSize();
}
