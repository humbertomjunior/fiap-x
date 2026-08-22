package br.com.fiapx.notification.service;

import br.com.fiapx.common.event.VideoStatusEvent;
import br.com.fiapx.notification.domain.NotificationHistory;
import br.com.fiapx.notification.dto.NotificationResponseDTO;
import br.com.fiapx.notification.repository.NotificationHistoryRepository;
import br.com.fiapx.notification.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @Mock
    private NotificationEmailSender notificationEmailSender;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldPersistAndSendFinishedNotification() {
        VideoStatusEvent event = event("FINISHED", null);

        notificationService.process(event);

        ArgumentCaptor<NotificationHistory> captor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository).save(captor.capture());
        NotificationHistory history = captor.getValue();
        assertThat(history.getStatus()).isEqualTo("FINISHED");
        assertThat(history.getSubject()).isEqualTo("Video processing finished");
        assertThat(history.getMessage()).isEqualTo("Your video has been processed successfully.");
        verify(notificationEmailSender).send(event, "FINISHED", "Video processing finished", "Your video has been processed successfully.");
    }

    @Test
    void shouldNormalizeUnknownStatusToErrorAndUseFallbackMessage() {
        VideoStatusEvent event = event("BROKEN", " ");

        notificationService.process(event);

        ArgumentCaptor<NotificationHistory> captor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository).save(captor.capture());
        NotificationHistory history = captor.getValue();
        assertThat(history.getStatus()).isEqualTo("ERROR");
        assertThat(history.getSubject()).isEqualTo("Video processing failed");
        assertThat(history.getMessage()).isEqualTo("Your video processing failed.");
    }

    @Test
    void shouldUseEventTimestampWhenPersistingHistory() {
        LocalDateTime updatedAt = LocalDateTime.now().minusMinutes(5);
        VideoStatusEvent event = new VideoStatusEvent(UUID.randomUUID(), UUID.randomUUID(), "user@fiapx.com", "ERROR", null, "ffmpeg failed", updatedAt);

        notificationService.process(event);

        ArgumentCaptor<NotificationHistory> captor = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void shouldListNotificationsForUser() {
        UUID userId = UUID.randomUUID();
        NotificationHistory history = new NotificationHistory();
        history.setId(UUID.randomUUID());
        history.setVideoId(UUID.randomUUID());
        history.setStatus("FINISHED");
        history.setSubject("Done");
        history.setMessage("Processed");
        history.setCreatedAt(LocalDateTime.now());
        when(notificationHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(history));

        List<NotificationResponseDTO> response = notificationService.listUserNotifications(new AuthenticatedUser(userId, "user@fiapx.com"));

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().subject()).isEqualTo("Done");
    }

    private VideoStatusEvent event(String status, String errorMessage) {
        return new VideoStatusEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@fiapx.com",
                status,
                "/tmp/video.zip",
                errorMessage,
                LocalDateTime.now()
        );
    }
}
