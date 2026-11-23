package com.gsms.module.notify.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 推送定时器：每分钟检查一次是否有配置到点推送 */
@Slf4j
@Component
public class NotifyScheduler {

    private final NotifyService notifyService;

    public NotifyScheduler(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    /** 每分钟第 10 秒执行，避开整点拥堵 */
    @Scheduled(cron = "10 * * * * ?")
    public void run() {
        try {
            notifyService.scheduledPush();
        } catch (Exception e) {
            log.error("定时推送任务异常", e);
        }
    }
}
