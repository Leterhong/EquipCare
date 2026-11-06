package com.equipcare.system.service;

import com.equipcare.system.vo.DeptTreeVO;

import java.util.List;

/**
 * 部门服务。
 */
public interface DeptService {

    /** 部门树 */
    List<DeptTreeVO> tree();
}
