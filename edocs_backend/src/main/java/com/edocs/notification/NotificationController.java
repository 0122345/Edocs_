package com.edocs.notification;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.edocs.security.CurrentUser;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NotificationDto(String id, NotificationType type, Channel channel, String title, String body, String link,
            Instant createdAt, boolean read) {
    }

    public record MarkReadRequest(List<String> ids) {
    }

    @GetMapping
    public List<NotificationDto> list() {
        return notifications.listFor(CurrentUser.get().userId()).stream()
                .map(n -> new NotificationDto(n.getId(), n.getType(), n.getChannel(), n.getTitle(), n.getBody(), n.getLink(), n.getCreatedAt(), n.isRead()))
                .toList();
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@RequestBody(required = false) MarkReadRequest req) {
        notifications.markRead(CurrentUser.get().userId(), req == null ? null : req.ids());
    }
}
