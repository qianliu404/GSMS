package com.gsms.module.system.controller;

import com.gsms.common.PageResult;
import com.gsms.common.R;
import com.gsms.module.system.dto.DictItemRequest;
import com.gsms.module.system.dto.RoleCreateRequest;
import com.gsms.module.system.dto.RolePermissionRequest;
import com.gsms.module.system.dto.UserDTO;
import com.gsms.module.system.entity.SysDept;
import com.gsms.module.system.entity.SysPermission;
import com.gsms.module.system.entity.SysDict;
import com.gsms.module.system.entity.SysRole;
import com.gsms.module.system.service.DeptService;
import com.gsms.module.system.service.DictService;
import com.gsms.module.system.service.PermissionService;
import com.gsms.module.system.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SystemController {

    private final UserService userService;
    private final DictService dictService;
    private final DeptService deptService;
    private final PermissionService permissionService;

    public SystemController(UserService userService, DictService dictService, DeptService deptService,
                            PermissionService permissionService) {
        this.userService = userService;
        this.dictService = dictService;
        this.deptService = deptService;
        this.permissionService = permissionService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<PageResult<UserDTO.UserVO>> pageUsers(UserDTO.Query query) {
        return R.ok(userService.page(query));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Long> createUser(@Valid @RequestBody UserDTO.SaveRequest request) {
        return R.ok(userService.create(request));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserDTO.SaveRequest request) {
        userService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Void> changeStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        userService.changeStatus(id, body.get("status"));
        return R.ok();
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userService.resetPassword(id, body.get("password"));
        return R.ok();
    }

    /** 权限项清单：系统管理员配置角色权限时用 */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<List<SysPermission>> listPermissions() {
        return R.ok(permissionService.listAll());
    }

    /** 某角色已授权的权限码 */
    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<List<String>> rolePermissions(@PathVariable Long roleId) {
        return R.ok(permissionService.rolePermissions(roleId));
    }

    /** 全量覆盖角色授权，保存后相关用户需重新登录刷新权限 */
    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Void> saveRolePermissions(@PathVariable Long roleId,
                                       @Valid @RequestBody RolePermissionRequest request) {
        permissionService.save(roleId, request.getPermCodes());
        return R.ok();
    }

    /** 新建自定义角色：编码系统生成，可选复制现有角色权限与菜单 */
    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<SysRole> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return R.ok(permissionService.create(request));
    }

    /** 删除自定义角色（内置角色与仍挂载用户的角色不可删除） */
    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Void> deleteRole(@PathVariable Long roleId) {
        permissionService.delete(roleId);
        return R.ok();
    }

    @GetMapping("/roles")
    public R<List<SysRole>> listRoles() {
        return R.ok(userService.listRoles());
    }

    /** 人员选择项：所有登录用户可用，仅返回姓名与部门 */
    @GetMapping("/users/options")
    public R<List<Map<String, Object>>> userOptions() {
        return R.ok(userService.listOptions());
    }

    @GetMapping("/depts")
    public R<List<SysDept>> listDepts() {
        return R.ok(deptService.listAll());
    }

    /** 新增部门：用户/案件建档时可直接选用，仅系统管理员可维护 */
    @PostMapping("/depts")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Long> createDept(@Valid @RequestBody DictItemRequest request) {
        return R.ok(deptService.create(request.getName()));
    }

    /** 删除部门：存在下级部门或仍有用户归属时拒绝 */
    @DeleteMapping("/depts/{id}")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Void> removeDept(@PathVariable Long id) {
        deptService.delete(id);
        return R.ok();
    }

    @GetMapping("/dicts")
    public R<Map<String, List<SysDict>>> listDicts() {
        return R.ok(dictService.listAll());
    }

    @GetMapping("/dicts/{type}")
    public R<List<SysDict>> listDictByType(@PathVariable String type) {
        return R.ok(dictService.listByType(type));
    }

    /** 新增自定义字典项（如厂区），建档时可直接带出新建的值 */
    @PostMapping("/dicts/{type}/items")
    public R<SysDict> addDictItem(@PathVariable String type, @Valid @RequestBody DictItemRequest request) {
        return R.ok(dictService.addItem(type, request.getName()));
    }

    /** 删除自定义字典项，仅系统管理员可操作 */
    @DeleteMapping("/dicts/{type}/items/{id}")
    @PreAuthorize("hasAuthority('ROLE_SYS_ADMIN')")
    public R<Void> removeDictItem(@PathVariable String type, @PathVariable Long id) {
        dictService.removeItem(type, id);
        return R.ok();
    }
}
