package com.gsms.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.module.audit.service.AuditService;
import com.gsms.module.system.entity.SysDept;
import com.gsms.module.system.entity.SysUser;
import com.gsms.module.system.mapper.SysDeptMapper;
import com.gsms.module.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DeptService {

    private final SysDeptMapper deptMapper;
    private final SysUserMapper userMapper;
    private final AuditService auditService;

    public DeptService(SysDeptMapper deptMapper, SysUserMapper userMapper, AuditService auditService) {
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    public List<SysDept> listAll() {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getStatus, 1)
                .orderByAsc(SysDept::getSort));
    }

    /** 新增部门：名称去重，编码自动生成，挂在根节点下（当前组织为单层） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(String deptName) {
        String name = deptName == null ? "" : deptName.trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门名称不能为空");
        }
        if (name.length() > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "部门名称不能超过 100 字");
        }
        Long exists = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getDeptName, name));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.DUPLICATE, "部门「" + name + "」已存在");
        }

        SysDept entity = new SysDept();
        entity.setDeptCode("D" + System.currentTimeMillis());
        entity.setDeptName(name);
        entity.setParentId(0L);
        entity.setSort(maxSort() + 1);
        entity.setStatus(1);
        deptMapper.insert(entity);

        auditService.success("部门管理", "新增部门", String.valueOf(entity.getId()), "deptName=" + name);
        return entity.getId();
    }

    /** 删除部门：存在下级部门或仍有用户归属时拒绝，避免组织数据悬空 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysDept entity = deptMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "部门不存在");
        }
        Long children = deptMapper.selectCount(new LambdaQueryWrapper<SysDept>()
                .eq(SysDept::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ErrorCode.STATUS_CONFLICT, "该部门下存在下级部门，无法删除");
        }
        Long users = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id));
        if (users != null && users > 0) {
            throw new BizException(ErrorCode.STATUS_CONFLICT,
                    String.format("该部门下仍有 %d 个用户，请先调整其所属部门", users));
        }
        deptMapper.deleteById(id);
        auditService.success("部门管理", "删除部门", String.valueOf(id), "deptName=" + entity.getDeptName());
    }

    private int maxSort() {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .orderByDesc(SysDept::getSort)
                        .last("LIMIT 1"))
                .stream().findFirst().map(d -> d.getSort() == null ? 0 : d.getSort()).orElse(0);
    }

    /**
     * 取本部门及全部子孙部门ID。部门表数据量小（企业内组织），
     * 一次性加载后在内存递归，避免多次往返数据库。
     */
    public List<Long> selfAndDescendantIds(Long deptId) {
        if (deptId == null) {
            return Collections.emptyList();
        }
        List<SysDept> all = listAll();
        List<Long> result = new ArrayList<>();
        collect(all, deptId, result);
        return result;
    }

    private void collect(List<SysDept> all, Long current, List<Long> result) {
        if (result.contains(current)) {
            return;
        }
        result.add(current);
        for (SysDept dept : all) {
            if (current.equals(dept.getParentId())) {
                collect(all, dept.getId(), result);
            }
        }
    }
}
