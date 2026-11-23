package com.gsms.module.collab.controller;

import com.gsms.common.R;
import com.gsms.common.UserContext;
import com.gsms.module.collab.dto.CollabDTO;
import com.gsms.module.collab.entity.Notification;
import com.gsms.module.collab.service.CollabService;
import com.gsms.module.collab.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CollabController {

    private final CollabService collabService;
    private final NotificationService notificationService;

    public CollabController(CollabService collabService, NotificationService notificationService) {
        this.collabService = collabService;
        this.notificationService = notificationService;
    }

    @PostMapping("/cases/{caseId}/tasks")
    @PreAuthorize("hasAuthority('todo:create')")
    public R<Long> createTask(@PathVariable Long caseId,
                              @Valid @RequestBody CollabDTO.TaskCreateRequest request) {
        return R.ok(collabService.createTask(caseId, request));
    }

    @GetMapping("/cases/{caseId}/tasks")
    @PreAuthorize("hasAuthority('todo:view')")
    public R<List<CollabDTO.TaskVO>> listTasks(@PathVariable Long caseId) {
        return R.ok(collabService.listTasks(caseId));
    }

    @PutMapping("/tasks/{id}/finish")
    @PreAuthorize("hasAuthority('todo:edit')")
    public R<Void> finishTask(@PathVariable Long id,
                              @RequestBody(required = false) CollabDTO.FinishRequest request) {
        collabService.finishTask(id, request == null ? null : request.getResult());
        return R.ok();
    }

    @PostMapping("/cases/{caseId}/countersigns/initiate")
    @PreAuthorize("hasAuthority('todo:create')")
    public R<Integer> initiate(@PathVariable Long caseId,
                               @RequestParam(value = "nodeCode", required = false) String nodeCode) {
        return R.ok(collabService.initiateCountersign(caseId, nodeCode));
    }

    @PostMapping("/cases/{caseId}/countersigns")
    @PreAuthorize("hasAuthority('todo:edit')")
    public R<Void> submitCountersign(@PathVariable Long caseId,
                                     @Valid @RequestBody CollabDTO.CountersignRequest request) {
        collabService.submitCountersign(caseId, request);
        return R.ok();
    }

    @GetMapping("/cases/{caseId}/countersigns")
    @PreAuthorize("hasAuthority('todo:view')")
    public R<List<CollabDTO.CountersignVO>> listCountersigns(@PathVariable Long caseId) {
        return R.ok(collabService.listCountersigns(caseId));
    }

    @GetMapping("/todos")
    @PreAuthorize("hasAuthority('todo:view')")
    public R<CollabDTO.TodoSummary> todos() {
        return R.ok(collabService.myTodos());
    }

    @GetMapping("/notifications")
    public R<Map<String, Object>> notifications(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        String userId = UserContext.userId();
        List<Notification> list = notificationService.listMine(userId, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("unread", notificationService.unreadCount(userId));
        result.put("list", list);
        return R.ok(result);
    }

    @PutMapping("/notifications/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(UserContext.userId(), id);
        return R.ok();
    }

    @PutMapping("/notifications/read-all")
    public R<Void> markAllRead() {
        notificationService.markAllRead(UserContext.userId());
        return R.ok();
    }
}
