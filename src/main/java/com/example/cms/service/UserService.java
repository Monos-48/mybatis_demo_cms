package com.example.cms.service;

import com.example.cms.common.PageVO;
import com.example.cms.dto.UserCreateDTO;
import com.example.cms.dto.UserQueryDTO;
import com.example.cms.vo.UserVO;

import java.util.List;

/**
 * 用户管理业务。
 */
public interface UserService {

    PageVO<UserVO> page(UserQueryDTO query);

    UserVO create(UserCreateDTO dto);

    void updateStatus(Long id, Integer status);

    void updateRoles(Long id, List<Long> roleIds);

    void resetPassword(Long id, String newPassword);

    void delete(Long id);

    UserVO getDetail(Long id);
}
