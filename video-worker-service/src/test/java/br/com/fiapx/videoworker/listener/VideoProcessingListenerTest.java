package br.com.fiapx.videoworker.listener;

import br.com.fiapx.common.event.VideoProcessingEvent;
import br.com.fiapx.videoworker.service.VideoProcessingOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoProcessingListenerTest {

    @Test
    void shouldForwardSqsEventToOrchestrator() {
        VideoProcessingOrchestrator orchestrator = mock(VideoProcessingOrchestrator.class);
        VideoProcessingListener listener = new VideoProcessingListener(orchestrator, mock(Environment.class));
        VideoProcessingEvent event = new VideoProcessingEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@fiapx.com",
                "Video",
                "/tmp/original.mp4",
                LocalDateTime.now()
        );

        listener.handle(event);

        verify(orchestrator).process(event);
    }

    @Test
    void shouldRethrowProcessingFailureToAllowSqsRetry() {
        VideoProcessingOrchestrator orchestrator = mock(VideoProcessingOrchestrator.class);
        VideoProcessingListener listener = new VideoProcessingListener(orchestrator, mock(Environment.class));
        VideoProcessingEvent event = new VideoProcessingEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@fiapx.com",
                "Video",
                "/tmp/original.mp4",
                LocalDateTime.now()
        );
        doThrow(new IllegalStateException("boom")).when(orchestrator).process(event);

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}
