package com.equipcare.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.equipcare.system.entity.SysDept;
import com.equipcare.system.mapper.SysDeptMapper;
import com.equipcare.system.service.DeptService;
import com.equipcare.system.vo.DeptTreeVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门树实现。
 */
@Service
public class DeptServiceImpl implements DeptService {

    private final SysDeptMapper deptMapper;

    public DeptServiceImpl(SysDeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    @Override
    public List<DeptTreeVO> tree() {
        List<SysDept> depts = deptMapper.selectList(Wrappers.<SysDept>lambdaQuery()
                .orderByAsc(SysDept::getSort));
        Map<Long, DeptTreeVO> byId = new LinkedHashMap<>();
        for (SysDept dept : depts) {
            DeptTreeVO node = new DeptTreeVO();
            node.setId(dept.getId());
            node.setParentId(dept.getParentId());
            node.setDeptName(dept.getDeptName());
            node.setDeptCode(dept.getDeptCode());
            node.setLeaderUserId(dept.getLeaderUserId());
            node.setSort(dept.getSort());
            node.setStatus(dept.getStatus());
            byId.put(dept.getId(), node);
        }
        List<DeptTreeVO> roots = new ArrayList<>();
        for (DeptTreeVO node : byId.values()) {
            DeptTreeVO parent = node.getParentId() == null ? null : byId.get(node.getParentId());
            if (parent != null && !parent.getId().equals(node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
