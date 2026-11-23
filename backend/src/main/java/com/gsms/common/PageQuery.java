package com.gsms.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 通用分页参数。pageSize 上限 100，防止前端传入过大值拖垮单机 MySQL。
 */
@Data
public class PageQuery {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private Integer pageNum = 1;
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    public <T> Page<T> toPage() {
        int num = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : pageSize;
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        return new Page<>(num, size);
    }
}
