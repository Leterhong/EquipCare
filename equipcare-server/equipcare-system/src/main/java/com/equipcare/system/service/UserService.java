package com.equipcare.system.service;

import com.equipcare.common.result.PageResult;
import com.equipcare.system.dto.UserCreateDTO;
import com.equipcare.system.dto.UserQuery;
import com.equipcare.system.dto.UserUpdateDTO;
import com.equipcare.system.vo.UserOptionVO;
import com.equipcare.system.vo.UserVO;

import java.util.List;

/**
 * 用户管理服务。
 */
public interface UserService {

    PageResult<UserVO> page(UserQuery query);

    UserVO detail(Long id);

    Long create(UserCreateDTO dto);

    void update(Long id, UserUpdateDTO dto);

    /** 启用/禁用。禁用后立即强制下线 */
    void changeStatus(Long id, Integer status);

    /** 管理员重置密码，强制下线该用户全部会话 */
    void resetPassword(Long id, String newPassword);

    /** 逻辑删除，同时释放登录名占用 */
    void delete(Long id);

    /** 人员候选项（仅 id / 姓名 / 部门），用于设备责任人等选择场景 */
    List<UserOptionVO> options(String keyword);
}
