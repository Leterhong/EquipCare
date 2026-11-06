package com.equipcare.asset.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.asset.dto.LocationSaveDTO;
import com.equipcare.asset.entity.EqLocation;
import com.equipcare.asset.mapper.EqEquipmentMapper;
import com.equipcare.asset.mapper.EqLocationMapper;
import com.equipcare.asset.service.LocationService;
import com.equipcare.asset.vo.LocationTreeVO;
import com.equipcare.common.exception.BizException;
import com.equipcare.common.query.GroupCount;
import com.equipcare.common.result.ResultCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 位置实现。
 * <p>位置采用「邻接表 + 冗余全路径」：写入时同步维护 {@code full_path}，
 * 详情与列表查询不再递归，符合第一阶段设计中对高频查询的性能要求。
 */
@Service
public class LocationServiceImpl implements LocationService {

    private static final int MAX_PATH_LENGTH = 300;

    private final EqLocationMapper locationMapper;
    private final EqEquipmentMapper equipmentMapper;

    public LocationServiceImpl(EqLocationMapper locationMapper, EqEquipmentMapper equipmentMapper) {
        this.locationMapper = locationMapper;
        this.equipmentMapper = equipmentMapper;
    }

    @Override
    public List<LocationTreeVO> tree() {
        List<EqLocation> locations = locationMapper.selectList(Wrappers.<EqLocation>lambdaQuery()
                .orderByAsc(EqLocation::getSort)
                .orderByAsc(EqLocation::getId));
        Map<Long, Long> counts = new HashMap<>();
        for (GroupCount gc : equipmentMapper.countGroupByLocation()) {
            if (gc.getId() != null) {
                counts.put(gc.getId(), gc.getTotal());
            }
        }

        Map<Long, LocationTreeVO> byId = new LinkedHashMap<>();
        for (EqLocation l : locations) {
            LocationTreeVO node = new LocationTreeVO();
            node.setId(l.getId());
            node.setParentId(l.getParentId() == null ? 0L : l.getParentId());
            node.setLocationCode(l.getLocationCode());
            node.setLocationName(l.getLocationName());
            node.setLocationType(l.getLocationType());
            node.setFullPath(l.getFullPath());
            node.setSort(l.getSort());
            node.setStatus(l.getStatus());
            node.setEquipmentCount(counts.getOrDefault(l.getId(), 0L).intValue());
            byId.put(l.getId(), node);
        }

        List<LocationTreeVO> roots = new ArrayList<>();
        for (LocationTreeVO node : byId.values()) {
            LocationTreeVO parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            if (parent != null && !parent.getId().equals(node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(LocationSaveDTO dto) {
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();
        String parentPath = "";
        if (parentId != 0L) {
            EqLocation parent = locationMapper.selectById(parentId);
            if (parent == null) {
                throw BizException.notFound("父级位置");
            }
            parentPath = parent.getFullPath();
        }

        Long duplicated = locationMapper.selectCount(Wrappers.<EqLocation>lambdaQuery()
                .eq(EqLocation::getLocationCode, dto.getLocationCode())
                .ne(dto.getId() != null, EqLocation::getId, dto.getId()));
        if (duplicated != null && duplicated > 0) {
            throw BizException.duplicate("位置编码 " + dto.getLocationCode());
        }

        String fullPath = parentPath == null || parentPath.isEmpty()
                ? dto.getLocationName()
                : parentPath + "/" + dto.getLocationName();
        if (fullPath.length() > MAX_PATH_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST, "位置路径超过 " + MAX_PATH_LENGTH + " 字符，请调整层级命名");
        }

        EqLocation entity = new EqLocation();
        entity.setParentId(parentId);
        entity.setLocationCode(dto.getLocationCode());
        entity.setLocationName(dto.getLocationName());
        entity.setLocationType(dto.getLocationType());
        entity.setFullPath(fullPath);
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());

        if (dto.getId() == null) {
            locationMapper.insert(entity);
            return entity.getId();
        }

        EqLocation existing = locationMapper.selectById(dto.getId());
        if (existing == null) {
            throw BizException.notFound("位置");
        }
        if (dto.getId().equals(parentId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "父级位置不能是自己");
        }
        if (parentId != 0L && isDescendant(dto.getId(), parentId)) {
            throw new BizException(ResultCode.BAD_REQUEST, "父级位置不能是当前位置的子孙节点");
        }
        entity.setId(dto.getId());
        locationMapper.updateById(entity);

        // 名称或父级变化会影响子孙节点的全路径，逐层刷新
        if (!fullPath.equals(existing.getFullPath())) {
            refreshChildrenPath(dto.getId(), fullPath);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        EqLocation existing = locationMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("位置");
        }
        Long children = locationMapper.selectCount(Wrappers.<EqLocation>lambdaQuery()
                .eq(EqLocation::getParentId, id));
        if (children != null && children > 0) {
            throw BizException.inUse("该位置下存在子位置，请先删除子位置");
        }
        int used = equipmentMapper.countByLocation(id);
        if (used > 0) {
            throw BizException.inUse("该位置下存在 " + used + " 台设备，无法删除");
        }
        locationMapper.deleteById(id);
    }

    /**
     * 递归刷新子孙节点的 full_path。
     */
    private void refreshChildrenPath(Long parentId, String parentPath) {
        List<EqLocation> children = locationMapper.selectList(Wrappers.<EqLocation>lambdaQuery()
                .eq(EqLocation::getParentId, parentId));
        for (EqLocation child : children) {
            String childPath = parentPath + "/" + child.getLocationName();
            if (childPath.length() > MAX_PATH_LENGTH) {
                throw new BizException(ResultCode.BAD_REQUEST,
                        "位置「" + child.getLocationName() + "」路径超过 " + MAX_PATH_LENGTH + " 字符");
            }
            if (childPath.equals(child.getFullPath())) {
                continue;
            }
            EqLocation update = new EqLocation();
            update.setId(child.getId());
            update.setFullPath(childPath);
            locationMapper.updateById(update);
            refreshChildrenPath(child.getId(), childPath);
        }
    }

    private boolean isDescendant(Long id, Long candidateParentId) {
        Long cursor = candidateParentId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 32) {
            if (cursor.equals(id)) {
                return true;
            }
            EqLocation node = locationMapper.selectById(cursor);
            if (node == null) {
                return false;
            }
            cursor = node.getParentId();
        }
        return false;
    }
}
