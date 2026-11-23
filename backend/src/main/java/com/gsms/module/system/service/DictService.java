package com.gsms.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.module.system.entity.SysDict;
import com.gsms.module.system.mapper.SysDictMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DictService {

    /** 允许业务侧自定义维护的字典类型，其余字典不开放写接口 */
    private static final List<String> EDITABLE_TYPES = Arrays.asList("plant_area");

    private final SysDictMapper dictMapper;

    public DictService(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    public List<SysDict> listByType(String type) {
        return dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSort));
    }

    public Map<String, List<SysDict>> listAll() {
        List<SysDict> all = dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getDictType)
                .orderByAsc(SysDict::getSort));
        Map<String, List<SysDict>> result = new LinkedHashMap<>();
        for (SysDict item : all) {
            result.computeIfAbsent(item.getDictType(), k -> new ArrayList<>()).add(item);
        }
        return result;
    }

    /** 新增字典项：同名幂等，编码自动生成 */
    public SysDict addItem(String type, String name) {
        assertEditable(type);
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "名称不能为空");
        }
        if (value.length() > 100) {
            throw new BizException(ErrorCode.PARAM_INVALID, "名称不能超过 100 字");
        }
        List<SysDict> exists = listByType(type);
        for (SysDict item : exists) {
            if (value.equals(item.getDictName())) {
                return item;
            }
        }
        SysDict item = new SysDict();
        item.setDictType(type);
        item.setDictCode("C" + System.currentTimeMillis());
        item.setDictName(value);
        item.setSort(exists.stream().mapToInt(SysDict::getSort).max().orElse(0) + 1);
        item.setStatus(1);
        dictMapper.insert(item);
        return item;
    }

    /** 删除字典项：仅删除选项，历史业务数据保存的是名称字符串，不受影响 */
    public void removeItem(String type, Long id) {
        assertEditable(type);
        SysDict item = dictMapper.selectById(id);
        if (item == null || !type.equals(item.getDictType())) {
            throw new BizException(ErrorCode.NOT_FOUND, "字典项不存在");
        }
        dictMapper.deleteById(id);
    }

    private void assertEditable(String type) {
        if (!EDITABLE_TYPES.contains(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该字典不支持自定义维护");
        }
    }
}
