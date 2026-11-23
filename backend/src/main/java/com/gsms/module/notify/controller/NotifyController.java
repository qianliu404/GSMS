package com.gsms.module.notify.controller;

import com.gsms.common.R;
import com.gsms.module.notify.dto.NotifyPreviewVO;
import com.gsms.module.notify.entity.NotifyConfig;
import com.gsms.module.notify.entity.NotifyHistory;
import com.gsms.module.notify.service.NotifyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** 推送设置：配置管理、消息预览、测试推送、历史 */
@RestController
@RequestMapping("/api/v1/notify")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('notify:view')")
    public R<List<NotifyConfig>> list() {
        return R.ok(notifyService.list());
    }

    @GetMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('notify:view')")
    public R<NotifyConfig> get(@PathVariable Long id) {
        return R.ok(notifyService.get(id));
    }

    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('notify:edit')")
    public R<NotifyConfig> create(@RequestBody NotifyConfig config) {
        config.setId(null);
        return R.ok(notifyService.save(config));
    }

    @PutMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('notify:edit')")
    public R<NotifyConfig> update(@PathVariable Long id, @RequestBody NotifyConfig config) {
        config.setId(id);
        return R.ok(notifyService.save(config));
    }

    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('notify:edit')")
    public R<Void> delete(@PathVariable Long id) {
        notifyService.delete(id);
        return R.ok();
    }

    @PutMapping("/configs/{id}/enabled")
    @PreAuthorize("hasAuthority('notify:edit')")
    public R<Void> toggle(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        notifyService.toggle(id, body.get("enabled"));
        return R.ok();
    }

    /** 按未保存的表单参数预览（所见即所得） */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('notify:view')")
    public R<NotifyPreviewVO> preview(@RequestBody NotifyConfig config) {
        return R.ok(notifyService.preview(config));
    }

    /** 按已保存配置预览 */
    @GetMapping("/configs/{id}/preview")
    @PreAuthorize("hasAuthority('notify:view')")
    public R<NotifyPreviewVO> previewById(@PathVariable Long id) {
        return R.ok(notifyService.previewById(id));
    }

    /** 测试推送：立即发送一条，不改变 lastPushTime */
    @PostMapping("/configs/{id}/test")
    @PreAuthorize("hasAuthority('notify:edit')")
    public R<Map<String, Integer>> test(@PathVariable Long id) {
        int matched = notifyService.testPush(id);
        Map<String, Integer> data = new HashMap<>();
        data.put("matched", matched);
        return R.ok(data);
    }

    /** 运维：立即触发所有启用的配置 */
    @PostMapping("/push-all")
    @PreAuthorize("hasAnyAuthority('ROLE_SYS_ADMIN','notify:edit')")
    public R<Map<String, Integer>> pushAll() {
        return R.ok(notifyService.pushAll());
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('notify:view')")
    public R<List<NotifyHistory>> history() {
        return R.ok(notifyService.history());
    }
}
