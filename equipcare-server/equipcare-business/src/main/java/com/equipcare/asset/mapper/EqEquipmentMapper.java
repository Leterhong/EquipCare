package com.equipcare.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.equipcare.asset.dto.EquipmentQuery;
import com.equipcare.asset.entity.EqEquipment;
import com.equipcare.asset.vo.EquipmentVO;
import com.equipcare.common.query.GroupCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EqEquipmentMapper extends BaseMapper<EqEquipment> {

    /**
     * 设备分页：一次查询带出类别名与位置全路径，避免 N+1。
     */
    @Select("""
            <script>
            SELECT e.id, e.asset_no, e.equipment_name, e.category_id, c.category_name,
                   e.location_id, l.full_path AS location_path, e.dept_id,
                   e.brand, e.model, e.spec, e.status, e.health_score,
                   e.responsible_user_id, ru.real_name AS responsible_user_name,
                   e.last_inspect_time, e.next_inspect_time,
                   e.qr_token, e.create_time,
                   (SELECT f.file_url FROM eq_equipment_file ef
                      INNER JOIN sys_file f ON f.id = ef.file_id
                     WHERE ef.equipment_id = e.id AND ef.file_type = 'IMAGE'
                     ORDER BY ef.sort_no ASC, ef.id ASC LIMIT 1) AS cover_url
            FROM eq_equipment e
            LEFT JOIN eq_category c ON c.id = e.category_id
            LEFT JOIN eq_location l ON l.id = e.location_id
            LEFT JOIN sys_user ru ON ru.id = e.responsible_user_id
            WHERE e.deleted = 0
            <if test="q.keyword != null and q.keyword != ''">
              AND (e.asset_no LIKE CONCAT('%', #{q.keyword}, '%')
                   OR e.equipment_name LIKE CONCAT('%', #{q.keyword}, '%')
                   OR e.serial_no LIKE CONCAT('%', #{q.keyword}, '%'))
            </if>
            <if test="q.categoryId != null"> AND e.category_id = #{q.categoryId} </if>
            <if test="q.locationId != null"> AND e.location_id = #{q.locationId} </if>
            <if test="q.status != null and q.status != ''"> AND e.status = #{q.status} </if>
            <if test="q.deptId != null"> AND e.dept_id = #{q.deptId} </if>
            <if test="q.minHealth != null"> AND e.health_score &gt;= #{q.minHealth} </if>
            <if test="q.maxHealth != null"> AND e.health_score &lt;= #{q.maxHealth} </if>
            ORDER BY e.id DESC
            </script>
            """)
    IPage<EquipmentVO> selectEquipmentPage(IPage<EquipmentVO> page, @Param("q") EquipmentQuery query);

    /** 统计某类别下的设备数量，用于删除保护 */
    @Select("SELECT COUNT(1) FROM eq_equipment WHERE category_id = #{categoryId} AND deleted = 0")
    int countByCategory(@Param("categoryId") Long categoryId);

    /** 统计某位置下的设备数量，用于删除保护 */
    @Select("SELECT COUNT(1) FROM eq_equipment WHERE location_id = #{locationId} AND deleted = 0")
    int countByLocation(@Param("locationId") Long locationId);

    /** 按二维码令牌查询（扫码场景） */
    @Select("SELECT * FROM eq_equipment WHERE qr_token = #{qrToken} AND deleted = 0")
    EqEquipment selectByQrToken(@Param("qrToken") String qrToken);

    /** 各类别下设备数量，用于类别树展示与删除保护 */
    @Select("SELECT category_id AS id, COUNT(1) AS total FROM eq_equipment "
            + "WHERE deleted = 0 GROUP BY category_id")
    List<GroupCount> countGroupByCategory();

    /** 各位置下设备数量，用于位置树展示与删除保护 */
    @Select("SELECT location_id AS id, COUNT(1) AS total FROM eq_equipment "
            + "WHERE deleted = 0 GROUP BY location_id")
    List<GroupCount> countGroupByLocation();

    /** 取某前缀下最大的资产编号，用于生成流水号（含已逻辑删除，避免编号复用） */
    @Select("SELECT MAX(asset_no) FROM eq_equipment WHERE asset_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxAssetNoByPrefix(@Param("prefix") String prefix);

    /** 资产编号是否已被占用（含已逻辑删除，编号不允许复用） */
    @Select("SELECT COUNT(1) FROM eq_equipment WHERE asset_no = #{assetNo}")
    int countByAssetNoIncludeDeleted(@Param("assetNo") String assetNo);
}
