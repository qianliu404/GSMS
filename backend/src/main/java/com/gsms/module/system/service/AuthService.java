package com.gsms.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.common.LoginUser;
import com.gsms.common.UserContext;
import com.gsms.config.JwtTokenProvider;
import com.gsms.module.system.dto.*;
import com.gsms.module.system.entity.SysDept;
import com.gsms.module.system.entity.SysMenu;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysDeptMapper;
import com.gsms.module.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AuthService {

    /** 数据范围优先级：数字越大范围越宽 */
    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_DEPT = "DEPT";
    private static final String SCOPE_SELF = "SELF";

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(SysUserMapper userMapper, SysDeptMapper deptMapper,
                       PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserId, request.getUserId()));
        // 账号不存在与密码错误返回同一提示，避免账号枚举
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已停用，请联系系统管理员");
        }

        List<String> roles = userMapper.selectRoleCodes(user.getUserId());
        List<String> permissions = userMapper.selectPermissionCodes(user.getUserId());
        String dataScope = resolveDataScope(userMapper.selectDataScopes(user.getUserId()));

        LoginUser loginUser = new LoginUser(user.getId(), user.getUserId(), user.getUserName(),
                user.getDeptId(), roles, permissions, dataScope);

        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .set(SysUser::getLastLoginTime, LocalDateTime.now())
                .eq(SysUser::getId, user.getId()));

        LoginResponse response = new LoginResponse();
        response.setToken(tokenProvider.generate(loginUser));
        response.setExpiresIn(tokenProvider.getExpireSeconds());
        response.setUser(toUserInfo(user, roles, dataScope, permissions));
        log.info("用户登录成功: {}", user.getUserId());
        return response;
    }

    public UserInfoVO currentUser() {
        LoginUser loginUser = UserContext.get();
        SysUser user = userMapper.selectById(loginUser.getId());
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "用户不存在或已被删除");
        }
        return toUserInfo(user, loginUser.getRoles(), loginUser.getDataScope(), loginUser.getPermissions());
    }

    private UserInfoVO toUserInfo(SysUser user, List<String> roles, String dataScope, List<String> permissions) {
        UserInfoVO vo = new UserInfoVO();
        vo.setId(user.getId());
        vo.setUserId(user.getUserId());
        vo.setUserName(user.getUserName());
        vo.setDeptId(user.getDeptId());
        vo.setMobile(user.getMobile());
        vo.setEmail(user.getEmail());
        vo.setPosition(user.getPosition());
        vo.setRoles(roles == null ? new ArrayList<String>() : roles);
        vo.setPermissions(permissions == null ? new ArrayList<String>() : permissions);
        vo.setDataScope(dataScope);
        vo.setCanViewSensitive(SCOPE_ALL.equals(dataScope));

        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getDeptName());
        }

        List<SysMenu> menus = userMapper.selectMenusByUserId(user.getUserId());
        List<MenuVO> menuVos = new ArrayList<>();
        for (SysMenu menu : menus) {
            MenuVO item = new MenuVO();
            item.setId(menu.getId());
            item.setMenuCode(menu.getMenuCode());
            item.setMenuName(menu.getMenuName());
            item.setPath(menu.getPath());
            item.setPermCode(menu.getPermCode());
            item.setIcon(menu.getIcon());
            item.setSort(menu.getSort());
            menuVos.add(item);
        }
        vo.setMenus(menuVos);
        return vo;
    }

    /** 多角色取最宽数据范围：ALL > DEPT > SELF */
    private String resolveDataScope(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return SCOPE_SELF;
        }
        String result = SCOPE_SELF;
        for (String scope : scopes) {
            if (SCOPE_ALL.equals(scope)) {
                return SCOPE_ALL;
            }
            if (SCOPE_DEPT.equals(scope)) {
                result = SCOPE_DEPT;
            }
        }
        return result;
    }
}
