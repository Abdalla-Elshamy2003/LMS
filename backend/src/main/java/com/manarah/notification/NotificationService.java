package com.manarah.notification;

import com.manarah.common.exception.ApiExceptions.NotFoundException;
import com.manarah.common.tenant.TenantContext;
import com.manarah.common.web.PageResponse;
import com.manarah.notification.channel.ExternalMessageSender;
import com.manarah.notification.domain.Notification;
import com.manarah.notification.repo.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Map<String, ExternalMessageSender> senders;

    public NotificationService(NotificationRepository notifications, List<ExternalMessageSender> senderList) {
        this.notifications = notifications;
        this.senders = senderList.stream().collect(Collectors.toMap(ExternalMessageSender::channel, Function.identity()));
    }

    /**
     * Raise a notification for a recipient across the requested channels; persists one row per
     * channel.
     *
     * <p>A recipient with no login of their own is supported deliberately: most guardians are
     * added at registration with a name and a phone number and never get an account, and they are
     * exactly the people the parent alerts exist for. For them the WhatsApp/SMS message is still
     * dispatched — only the in-app row is skipped, because {@code notifications.recipient_user_id}
     * is NOT NULL and there is no inbox to file it in.
     */
    @Transactional
    public void notify(Long tenantId, NotificationDtos.NotifyCommand cmd) {
        List<String> channels = (cmd.channels() == null || cmd.channels().isEmpty())
                ? List.of("IN_APP") : cmd.channels();
        for (String channel : channels) {
            boolean inApp = "IN_APP".equals(channel);
            boolean dispatched = false;
            if (!inApp) {
                ExternalMessageSender sender = senders.get(channel);
                dispatched = sender != null && sender.send(cmd.recipientContact(), cmd.title(), cmd.body());
            }
            if (cmd.recipientUserId() == null) {
                continue;
            }
            Notification n = new Notification();
            n.setTenantId(tenantId);
            n.setRecipientUserId(cmd.recipientUserId());
            n.setTitle(cmd.title());
            n.setBody(cmd.body());
            n.setCategory(cmd.category() == null ? "GENERAL" : cmd.category());
            n.setChannel(channel);
            n.setEntityType(cmd.entityType());
            n.setEntityId(cmd.entityId());
            n.setStatus(inApp || dispatched ? "SENT" : "PENDING");
            notifications.save(n);
        }
    }

    public PageResponse<NotificationDtos.NotificationView> inbox(Long userId, int page, int size) {
        Long tenantId = TenantContext.require();
        Page<Notification> p = notifications.findByTenantIdAndRecipientUserIdOrderByCreatedAtDesc(
                tenantId, userId, PageRequest.of(page, size));
        return PageResponse.of(p, this::toView);
    }

    public long unreadCount(Long userId) {
        return notifications.countByTenantIdAndRecipientUserIdAndStatus(TenantContext.require(), userId, "SENT");
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Long tenantId = TenantContext.require();
        Notification n = notifications.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> NotFoundException.of("الإشعار", id));
        if (!n.getRecipientUserId().equals(userId)) {
            throw NotFoundException.of("الإشعار", id);
        }
        n.setStatus("READ");
        n.setReadAt(Instant.now());
        notifications.save(n);
    }

    @Transactional
    public void markAllRead(Long userId) {
        Long tenantId = TenantContext.require();
        var unread = notifications.findByTenantIdAndRecipientUserIdAndStatusOrderByCreatedAtDesc(tenantId, userId, "SENT");
        Instant now = Instant.now();
        for (Notification n : unread) {
            n.setStatus("READ");
            n.setReadAt(now);
        }
        notifications.saveAll(unread);
    }

    private NotificationDtos.NotificationView toView(Notification n) {
        return new NotificationDtos.NotificationView(n.getId(), n.getTitle(), n.getBody(), n.getCategory(),
                n.getChannel(), n.getStatus(), n.getEntityType(), n.getEntityId(), n.getReadAt(), n.getCreatedAt());
    }
}
