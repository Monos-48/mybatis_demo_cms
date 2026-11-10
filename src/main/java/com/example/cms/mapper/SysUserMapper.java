package com.example.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cms.dto.UserQueryDTO;
import com.example.cms.entity.SysUser;
import com.example.cms.vo.UserRoleView;
import com.example.cms.vo.UserVO;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 用户持久层。复杂检索使用 XML，简单 CRUD 继承 BaseMapper。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 用户分页检索（关键字、状态、角色）。
     */
    IPage<UserVO> selectUserPage(Page<UserVO> page, @Param("q") UserQueryDTO query);

    /**
     * 批量查询用户角色，供页面内存组装，避免逐用户查询（N+1）。
     */
    List<UserRoleView> selectRolesByUserIds(@Param("userIds") Collection<Long> userIds);

    /**
     * 查询用户有效角色编码。
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
