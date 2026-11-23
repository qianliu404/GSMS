package com.gsms.module.collab.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gsms.module.collab.entity.Notification;
import com.gsms.module.collab.mapper.NotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内信通知。外部 IM / 邮件默认关闭，站内信为必达渠道。
 * 邮件与 IM 通道预留 channel 字段，启用时在此处扩展即可，不影响核心业务。
 */
@Slf4j
@Service
public class NotificationService {

    private static final String CHANNEL_INNER = "INNER";

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    public void sendInner(String receiverId, String title, String content, String templateCode, String bizId) {
        if (receiverId == null || receiverId.isEmpty()) {
            return;
        }
        Notification entity = new Notification();
        entity.setReceiverId(receiverId);
        entity.setChannel(CHANNEL_INNER);
        entity.setTemplateCode(templateCode);
        entity.setTitle(title);
        entity.setContent(content);
        entity.setBizId(bizId);
        entity.setSendStatus("SUCCESS");
        entity.setSendTime(LocalDateTime.now());
        entity.setRetryCount(0);
        entity.setReadFlag(0);
        entity.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(entity);
    }

    public List<Notification> listMine(String userId, int limit) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .orderByDesc(Notification::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100)));
    }

    public long unreadCount(String userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getChannel, CHANNEL_INNER)
                .eq(Notification::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    public void markRead(String userId, Long id) {
        Notification entity = notificationMapper.selectById(id);
        if (entity == null || !userId.equals(entity.getReceiverId())) {
            return;
        }
        entity.setReadFlag(1);
        notificationMapper.updateById(entity);
    }

    public void markAllRead(String userId) {
        List<Notification> unread = notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getReadFlag, 0));
        for (Notification item : unread) {
            item.setReadFlag(1);
            notificationMapper.updateById(item);
        }
    }
}
