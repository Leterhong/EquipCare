package com.equipcare.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.ai.audit.AiCallLog;

/** AI 调用审计 Mapper（包名匹配 @MapperScan 的 **.mapper 通配） */
public interface AiCallLogMapper extends BaseMapper<AiCallLog> {
}
