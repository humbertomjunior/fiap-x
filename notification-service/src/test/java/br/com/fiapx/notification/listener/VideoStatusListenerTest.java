package br.com.fiapx.notification.listener;

import br.com.fiapx.common.event.VideoStatusEvent;
import br.com.fiapx.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoStatusListenerTest {

    @Test
    void shouldForwardStatusEventToNotificationService() {
        NotificationService notificationService = mock(NotificationService.class);
        VideoStatusListener listener = new VideoStatusListener(notificationService);
        VideoStatusEvent event = new VideoStatusEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@fiapx.com",
                "FINISHED",
                "/tmp/video.zip",
                null,
                LocalDateTime.now()
        );

        listener.handle(event);

        verify(notificationService).process(event);
    }

    @Test
    void shouldRethrowFailureToAllowSqsRetry() {
        NotificationService notificationService = mock(NotificationService.class);
        VideoStatusListener listener = new VideoStatusListener(notificationService);
        VideoStatusEvent event = new VideoStatusEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@fiapx.com",
                "FINISHED",
                "/tmp/video.zip",
                null,
                LocalDateTime.now()
        );
        doThrow(new IllegalStateException("smtp down")).when(notificationService).process(event);

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("smtp down");
    }
}
