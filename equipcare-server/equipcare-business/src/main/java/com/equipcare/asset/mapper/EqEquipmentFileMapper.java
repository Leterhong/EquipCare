package com.equipcare.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.equipcare.asset.entity.EqEquipmentFile;
import com.equipcare.asset.vo.EquipmentFileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EqEquipmentFileMapper extends BaseMapper<EqEquipmentFile> {

    /**
     * 设备文件列表：一次 JOIN 取回文件元数据，避免 N+1。
     * <p>图片排在附件之前，同类按 sort_no、id 升序，首张图片即封面。
     */
    @Select("""
            SELECT ef.id, ef.equipment_id, ef.file_id, ef.file_type, ef.sort_no, ef.create_time,
                   f.file_name, f.file_size, f.content_type, f.file_url
            FROM eq_equipment_file ef
            INNER JOIN sys_file f ON f.id = ef.file_id
            WHERE ef.equipment_id = #{equipmentId}
            ORDER BY FIELD(ef.file_type, 'IMAGE', 'ATTACHMENT'), ef.sort_no ASC, ef.id ASC
            """)
    List<EquipmentFileVO> selectFilesByEquipment(@Param("equipmentId") Long equipmentId);

    /** 是否已绑定同一文件，用于幂等判断 */
    @Select("""
            SELECT COUNT(1)
            FROM eq_equipment_file
            WHERE equipment_id = #{equipmentId} AND file_id = #{fileId}
            """)
    int countBinding(@Param("equipmentId") Long equipmentId, @Param("fileId") Long fileId);

    /** 设备封面（首张图片）地址，供列表页展示缩略图 */
    @Select("""
            SELECT f.file_url
            FROM eq_equipment_file ef
            INNER JOIN sys_file f ON f.id = ef.file_id
            WHERE ef.equipment_id = #{equipmentId} AND ef.file_type = 'IMAGE'
            ORDER BY ef.sort_no ASC, ef.id ASC
            LIMIT 1
            """)
    String selectCoverUrl(@Param("equipmentId") Long equipmentId);
}
