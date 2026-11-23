package com.gsms.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果。分页默认 20 条，最大 100 条（页面清单 4.7）。
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private int pageNum;
    private int pageSize;
    private List<T> list = new ArrayList<>();

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        result.setList(page.getRecords());
        return result;
    }

    public static <T> PageResult<T> of(IPage<?> page, List<T> list) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPageNum((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        result.setList(list);
        return result;
    }
}
