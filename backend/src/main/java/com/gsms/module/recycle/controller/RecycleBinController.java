package com.gsms.module.recycle.controller;

import com.gsms.common.PageResult;
import com.gsms.common.R;
import com.gsms.module.recycle.dto.RecycleBatchRequest;
import com.gsms.module.recycle.dto.RecycleItemVO;
import com.gsms.module.recycle.dto.RecycleQuery;
import com.gsms.module.recycle.service.RecycleBinService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/** 回收站：已删除案件与用户的恢复 / 永久删除 */
@RestController
@RequestMapping("/api/v1/recycle-bin")
public class RecycleBinController {

    private final RecycleBinService recycleBinService;

    public RecycleBinController(RecycleBinService recycleBinService) {
        this.recycleBinService = recycleBinService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('recycle:view')")
    public R<PageResult<RecycleItemVO>> page(RecycleQuery query) {
        return R.ok(recycleBinService.page(query));
    }

    @PostMapping("/restore")
    @PreAuthorize("hasAuthority('recycle:edit')")
    public R<Integer> restore(@Valid @RequestBody RecycleBatchRequest request) {
        return R.ok(recycleBinService.restore(request));
    }

    /** 永久删除：数据库中一并清除，不可恢复 */
    @DeleteMapping
    @PreAuthorize("hasAuthority('recycle:delete')")
    public R<Integer> purge(@Valid @RequestBody RecycleBatchRequest request) {
        return R.ok(recycleBinService.purge(request));
    }
}
