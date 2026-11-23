package com.gsms.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.system.dto.RoleCreateRequest;
import com.gsms.module.system.entity.SysPermission;
import com.gsms.module.system.entity.SysRole;
import com.gsms.module.system.entity.SysRoleMenu;
import com.gsms.module.system.entity.SysRolePermission;
import com.gsms.module.system.entity.SysUserRole;
import com.gsms.module.system.mapper.SysPermissionMapper;
import com.gsms.module.system.mapper.SysRoleMapper;
import com.gsms.module.system.mapper.SysRoleMenuMapper;
import com.gsms.module.system.mapper.SysRolePermissionMapper;
import com.gsms.module.system.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 角色权限：全部权限项、角色已授权项、管理员保存授权，以及自定义角色的新建与删除 */
@Service
public class PermissionService {

    /** 自定义角色编码前缀：只有该前缀的角色允许删除，内置 8 角色受保护 */
    public static final String CUSTOM_ROLE_PREFIX = "ROLE_CUSTOM_";

    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final AuditService auditService;

    public PermissionService(SysPermissionMapper permissionMapper,
                             SysRolePermissionMapper rolePermissionMapper,
                             SysRoleMapper roleMapper,
                             SysRoleMenuMapper roleMenuMapper,
                             SysUserRoleMapper userRoleMapper,
                             AuditService auditService) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
        this.auditService = auditService;
    }

    public List<SysPermission> listAll() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getStatus, 1)
                .orderByAsc(SysPermission::getSort, SysPermission::getId));
    }

    public List<String> rolePermissions(Long roleId) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return permissionMapper.selectCodesByRoleId(roleId);
    }

    /** 全量覆盖角色授权：非法权限码直接丢弃，系统管理员不可清空以免锁死自己 */
    @Transactional(rollbackFor = Exception.class)
    public void save(Long roleId, List<String> permCodes) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        if ("ROLE_SYS_ADMIN".equals(role.getRoleCode()) && (permCodes == null || permCodes.isEmpty())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "系统管理员不能清空全部权限");
        }

        Set<String> valid = listAll().stream().map(SysPermission::getPermCode).collect(Collectors.toSet());
        Set<String> wanted = new HashSet<>();
        if (permCodes != null) {
            for (String code : permCodes) {
                if (code != null && valid.contains(code.trim())) {
                    wanted.add(code.trim());
                }
            }
        }

        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        for (String code : wanted) {
            SysPermission p = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                    .eq(SysPermission::getPermCode, code));
            if (p == null) continue;
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(p.getId());
            rolePermissionMapper.insert(rp);
        }
    }

    /** 新建自定义角色：编码由系统生成，可选复制现有角色的权限与菜单作初始配置 */
    @Transactional(rollbackFor = Exception.class)
    public SysRole create(RoleCreateRequest request) {
        String name = request.getRoleName().trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色名称不能为空");
        }
        Long duplicate = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleName, name));
        if (duplicate != null && duplicate > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "角色名称已存在：" + name);
        }

        String scope = request.getDataScope();
        if (scope == null || scope.isEmpty()) {
            scope = "SELF";
        }
        if (!"ALL".equals(scope) && !"DEPT".equals(scope) && !"SELF".equals(scope)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "数据范围只能是 ALL / DEPT / SELF");
        }

        SysRole role = new SysRole();
        role.setRoleCode(CUSTOM_ROLE_PREFIX + System.currentTimeMillis());
        role.setRoleName(name);
        role.setDataScope(scope);
        role.setStatus(1);
        role.setRemark(request.getRemark());
        roleMapper.insert(role);

        // 复制来源角色的权限码与侧栏菜单，让新角色建完即可用
        if (request.getCopyFromRoleId() != null) {
            SysRole source = roleMapper.selectById(request.getCopyFromRoleId());
            if (source == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "复制来源角色不存在");
            }
            List<SysRolePermission> perms = rolePermissionMapper.selectList(
                    new LambdaQueryWrapper<SysRolePermission>()
                            .eq(SysRolePermission::getRoleId, source.getId()));
            for (SysRolePermission p : perms) {
                SysRolePermission rp = new SysRolePermission();
                rp.setRoleId(role.getId());
                rp.setPermissionId(p.getPermissionId());
                rolePermissionMapper.insert(rp);
            }
            List<SysRoleMenu> menus = roleMenuMapper.selectList(
                    new LambdaQueryWrapper<SysRoleMenu>()
                            .eq(SysRoleMenu::getRoleId, source.getId()));
            for (SysRoleMenu m : menus) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(role.getId());
                rm.setMenuId(m.getMenuId());
                roleMenuMapper.insert(rm);
            }
        }

        auditService.success("角色管理", "新建角色", String.valueOf(role.getId()),
                "roleName=" + name + ", dataScope=" + scope
                        + (request.getCopyFromRoleId() != null ? ", copyFrom=" + request.getCopyFromRoleId() : ""));
        return role;
    }

    /** 删除自定义角色：内置角色不可删；仍有用户挂载时先要求移除用户 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        // 仅系统管理员角色受保护；其余内置角色与自定义角色均可删除
        if ("ROLE_SYS_ADMIN".equals(role.getRoleCode())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "系统管理员角色不可删除");
        }

        // 自动解除该角色下所有用户的挂载，避免留下指向不存在角色的脏数据
        Long users = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, roleId));
        int unlinked = users == null ? 0 : users.intValue();
        if (unlinked > 0) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getRoleId, roleId));
        }

        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId));
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId));
        roleMapper.deleteById(roleId);

        auditService.success("角色管理", "删除角色", String.valueOf(roleId),
                "roleName=" + role.getRoleName() + ", unlinkedUsers=" + unlinked);
    }
}
