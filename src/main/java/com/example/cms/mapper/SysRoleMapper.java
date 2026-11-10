package com.example.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.cms.entity.SysRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色持久层。
 */
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询角色已分配的权限 ID。
     */
    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);
}
