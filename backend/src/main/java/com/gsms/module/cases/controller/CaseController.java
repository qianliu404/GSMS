package com.gsms.module.cases.controller;

import com.gsms.common.PageResult;
import com.gsms.common.R;
import com.gsms.module.cases.dto.*;
import com.gsms.module.cases.service.CaseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('case:create')")
    public R<Long> create(@Valid @RequestBody CaseCreateRequest request) {
        return R.ok(caseService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('case:view')")
    public R<PageResult<CaseVO>> page(CaseQuery query) {
        return R.ok(caseService.page(query));
    }

    /** 关联案件选择器：按案号/当事人模糊匹配，供新建案件与争议关联页选案 */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('case:view')")
    public R<List<CaseOptionVO>> options(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer limit) {
        return R.ok(caseService.options(keyword, limit));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('case:view')")
    public R<CaseDetailVO> detail(@PathVariable Long id) {
        return R.ok(caseService.detail(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('case:edit')")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody CaseCreateRequest request) {
        caseService.update(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('case:edit')")
    public R<Void> submit(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        caseService.submit(id, body == null ? null : body.get("opinion"));
        return R.ok();
    }

    @PostMapping("/{id}/transition")
    @PreAuthorize("hasAuthority('case:edit')")
    public R<Void> transition(@PathVariable Long id, @Valid @RequestBody TransitionRequest request) {
        caseService.transition(id, request);
        return R.ok();
    }

    /** 办理区人工调整当前节点截止日期 */
    @PostMapping("/{id}/node-deadline")
    @PreAuthorize("hasAuthority('case:edit')")
    public R<CaseDetailVO.NodeDeadlineVO> adjustNodeDeadline(@PathVariable Long id,
                                                             @RequestBody Map<String, String> body) {
        return R.ok(caseService.adjustNodeDeadline(id,
                body == null ? null : body.get("nodeCode"),
                body == null ? null : body.get("deadlineDate"),
                body == null ? null : body.get("reason"),
                body == null ? null : body.get("handleDate")));
    }

    /** 提交开启当前节点：开启后才按截止日期判断是否超期 */
    @PostMapping("/{id}/node-open")
    @PreAuthorize("hasAuthority('case:edit')")
    public R<CaseDetailVO.NodeDeadlineVO> openNode(@PathVariable Long id,
                                                   @RequestBody(required = false) Map<String, String> body) {
        return R.ok(caseService.openNode(id, body == null ? null : body.get("nodeCode")));
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasAuthority('case:edit')")
    public R<Void> rollback(@PathVariable Long id, @Valid @RequestBody TransitionRequest.RollbackRequest request) {
        caseService.rollback(id, request.getToStatus(), request.getOpinion());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('case:delete')")
    public R<Void> delete(@PathVariable Long id) {
        caseService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('case:edit')")
    public R<Void> close(@PathVariable Long id, @RequestBody(required = false) TransitionRequest.CloseRequest request) {
        caseService.close(id, request);
        return R.ok();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('archive:edit')")
    public R<Void> archive(@PathVariable Long id) {
        caseService.archive(id);
        return R.ok();
    }

    @PostMapping("/{id}/relations")
    public R<Void> addRelation(@PathVariable Long id,
                               @Valid @RequestBody TransitionRequest.RelationRequest request) {
        caseService.addRelation(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}/relations/{relationId}")
    @PreAuthorize("hasAuthority('relation:delete')")
    public R<Void> removeRelation(@PathVariable Long id, @PathVariable Long relationId) {
        caseService.removeRelation(id, relationId);
        return R.ok();
    }

    /** 返回该案件可用的状态动作，供前端按钮动态渲染 */
    @GetMapping("/{id}/actions")
    @PreAuthorize("hasAuthority('case:view')")
    public R<List<CaseDetailVO.ActionVO>> actions(@PathVariable Long id) {
        return R.ok(caseService.detail(id).getActions());
    }
}
