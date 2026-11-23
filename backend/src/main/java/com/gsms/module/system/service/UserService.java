package com.gsms.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.common.PageResult;
import com.gsms.common.UserContext;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.system.dto.UserDTO;
import com.gsms.module.system.entity.*;
import com.gsms.module.system.mapper.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysDeptMapper deptMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(SysUserMapper userMapper, SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper, SysDeptMapper deptMapper,
                       PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.deptMapper = deptMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public PageResult<UserDTO.UserVO> page(UserDTO.Query query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysUser::getUserId, kw)
                    .or().like(SysUser::getUserName, kw)
                    .or().like(SysUser::getMobile, kw));
        }
        wrapper.eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .orderByDesc(SysUser::getId);

        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20 : query.getPageSize();
        pageSize = Math.min(pageSize, MAX_PAGE_SIZE);

        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageResult.of(page, Collections.<UserDTO.UserVO>emptyList());
        }

        List<String> userIds = page.getRecords().stream().map(SysUser::getUserId).collect(Collectors.toList());
        List<SysUserRole> relations = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds));
        Map<Long, SysRole> roleMap = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, r -> r, (a, b) -> a));
        Map<String, List<SysRole>> userRoles = new HashMap<>();
        for (SysUserRole relation : relations) {
            SysRole role = roleMap.get(relation.getRoleId());
            if (role != null) {
                userRoles.computeIfAbsent(relation.getUserId(), k -> new ArrayList<>()).add(role);
            }
        }
        Map<Long, String> deptNames = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));

        List<UserDTO.UserVO> list = new ArrayList<>();
        for (SysUser user : page.getRecords()) {
            list.add(toVO(user, userRoles.get(user.getUserId()), deptNames));
        }
        return PageResult.of(page, list);
    }

    private UserDTO.UserVO toVO(SysUser user, List<SysRole> roles, Map<Long, String> deptNames) {
        UserDTO.UserVO vo = new UserDTO.UserVO();
        vo.setId(user.getId());
        vo.setUserId(user.getUserId());
        vo.setUserName(user.getUserName());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(user.getDeptId() == null ? null : deptNames.get(user.getDeptId()));
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setPosition(user.getPosition());
        vo.setStatus(user.getStatus());
        vo.setLastLoginTime(user.getLastLoginTime());
        vo.setCreateTime(user.getCreateTime());
        if (roles != null) {
            vo.setRoleCodes(roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList()));
            vo.setRoleNames(roles.stream().map(SysRole::getRoleName).collect(Collectors.toList()));
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(UserDTO.SaveRequest req) {
        if (!StringUtils.hasText(req.getPassword())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "新建用户必须设置初始密码");
        }
        // 含已软删除账号一起查重，否则会出现唯一键冲突的 500
        if (userMapper.countByUserIdIgnoreDeleted(req.getUserId()) > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "登录名已存在（可能为已删除账号，请更换登录名）");
        }
        SysUser entity = new SysUser();
        entity.setUserId(req.getUserId());
        entity.setUserName(req.getUserName());
        entity.setDeptId(req.getDeptId());
        entity.setMobile(req.getMobile());
        entity.setEmail(req.getEmail());
        entity.setPosition(req.getPosition());
        entity.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        entity.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(0);
        userMapper.insert(entity);
        assignRoles(req.getUserId(), req.getRoleIds());
        auditService.success("用户管理", "新建用户", req.getUserId(), "userId=" + req.getUserId());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserDTO.SaveRequest req) {
        SysUser entity = userMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        entity.setUserName(req.getUserName());
        entity.setDeptId(req.getDeptId());
        entity.setMobile(req.getMobile());
        entity.setEmail(req.getEmail());
        entity.setPosition(req.getPosition());
        if (req.getStatus() != null) {
            entity.setStatus(req.getStatus());
        }
        if (StringUtils.hasText(req.getPassword())) {
            entity.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);

        if (req.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, entity.getUserId()));
            assignRoles(entity.getUserId(), req.getRoleIds());
        }
        auditService.success("用户管理", "修改用户", entity.getUserId(), "userId=" + entity.getUserId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        SysUser entity = userMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if ("admin".equals(entity.getUserId()) && status != null && status == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "内置管理员账号不可停用");
        }
        entity.setStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);
        auditService.success("用户管理", status == 1 ? "启用用户" : "停用用户", entity.getUserId(), null);
    }

    /**
     * 删除用户：软删除（deleted=1），账号与角色关系保留以便追溯。
     * 拦截：当前登录账号、内置 admin、最后一个启用的系统管理员、仍有在办案件的承办人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysUser entity = userMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (entity.getUserId().equals(UserContext.get().getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能删除当前登录的账号");
        }
        if ("admin".equals(entity.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "内置管理员账号不可删除");
        }
        boolean sysAdmin = userMapper.selectRoleCodes(entity.getUserId()).contains("ROLE_SYS_ADMIN");
        if (sysAdmin && userMapper.countEnabledSysAdmin() <= 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "系统至少需保留一个启用的系统管理员");
        }
        long activeCases = userMapper.countActiveCases(entity.getUserId());
        if (activeCases > 0) {
            throw new BizException(ErrorCode.STATUS_CONFLICT,
                    String.format("该账号仍是 %d 件在办案件的承办人/负责人，请先转办或将账号改为停用", activeCases));
        }
        // 显式写入 deleted 与 update_time：回收站需要展示删除时间
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getDeleted, 1)
                .set(SysUser::getUpdateTime, LocalDateTime.now()));
        auditService.success("用户管理", "删除用户", entity.getUserId(), "软删除，进入回收站");
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码长度不能少于 6 位");
        }
        SysUser entity = userMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        entity.setPasswordHash(passwordEncoder.encode(newPassword));
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);
        // 密码本身不入审计日志
        auditService.success("用户管理", "重置密码", entity.getUserId(), null);
    }

    private void assignRoles(String userId, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, 1)
                .orderByAsc(SysRole::getId));
    }

    /**
     * 人员选择项：供案件建档选择承办人/协办人/会签人/阅知人。
     * 仅返回必要字段，不含手机号、邮箱、密码等敏感信息。
     */
    public List<Map<String, Object>> listOptions() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getDeptId)
                .orderByAsc(SysUser::getId));
        Map<Long, String> deptNames = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName, (a, b) -> a));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser user : users) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", user.getUserId());
            item.put("userName", user.getUserName());
            item.put("deptId", user.getDeptId());
            item.put("deptName", user.getDeptId() == null ? null : deptNames.get(user.getDeptId()));
            item.put("position", user.getPosition());
            result.add(item);
        }
        return result;
    }
}
