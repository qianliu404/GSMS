package com.gsms.module.deadline.controller;

import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.common.PageResult;
import com.gsms.common.R;
import com.gsms.module.cases.service.CaseService;
import com.gsms.module.deadline.dto.DeadlineQuery;
import com.gsms.module.deadline.dto.DeadlineVO;
import com.gsms.module.deadline.entity.DeadlineReminder;
import com.gsms.module.deadline.service.DeadlineService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/deadlines")
public class DeadlineController {

    private final DeadlineService deadlineService;
    private final CaseService caseService;

    public DeadlineController(DeadlineService deadlineService, CaseService caseService) {
        this.deadlineService = deadlineService;
        this.caseService = caseService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('deadline:view')")
    public R<PageResult<DeadlineVO>> page(DeadlineQuery query) {
        return R.ok(deadlineService.page(query));
    }

    @PostMapping("/{id}/supervise")
    @PreAuthorize("hasAuthority('deadline:edit')")
    public R<Void> supervise(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireAccessible(id);
        deadlineService.supervise(id, body.get("opinion"));
        return R.ok();
    }

    @PostMapping("/{id}/reason")
    @PreAuthorize("hasAuthority('deadline:edit')")
    public R<Void> registerReason(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requireAccessible(id);
        deadlineService.registerReason(id, body.get("reason"));
        return R.ok();
    }

    @PostMapping("/{id}/done")
    @PreAuthorize("hasAuthority('deadline:edit')")
    public R<Void> markDone(@PathVariable Long id) {
        requireAccessible(id);
        deadlineService.markDone(id);
        return R.ok();
    }

    /** 手动触发扫描，便于演示与运维排查 */
    @PostMapping("/scan")
    @PreAuthorize("hasAnyAuthority('ROLE_SYS_ADMIN','ROLE_CASE_ADMIN')")
    public R<Map<String, Integer>> scan() {
        int[] result = deadlineService.scan();
        Map<String, Integer> data = new HashMap<>();
        data.put("reminded", result[0]);
        data.put("overdue", result[1]);
        return R.ok(data);
    }

    /** 期限节点的任何写操作都必须先确认调用方能访问其所属案件 */
    private void requireAccessible(Long id) {
        DeadlineReminder entity = deadlineService.get(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "期限节点不存在");
        }
        caseService.requireAccessible(entity.getCaseId());
    }
}
