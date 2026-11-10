package com.example.cms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.cms.entity.SysPermission;
import com.example.cms.vo.PermissionVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限持久层。
 */
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 按用户查询其所有启用角色的权限码集合。
     */
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询角色已分配的权限明细。
     */
    List<PermissionVO> selectByRoleId(@Param("roleId") Long roleId);
}
