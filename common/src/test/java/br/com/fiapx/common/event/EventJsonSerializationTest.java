package br.com.fiapx.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventJsonSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void shouldSerializeAndDeserializeVideoProcessingEvent() throws Exception {
        VideoProcessingEvent event = new VideoProcessingEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@fiapx.com",
                "Video",
                "/tmp/video.mp4",
                LocalDateTime.of(2026, 8, 22, 10, 30)
        );

        String json = objectMapper.writeValueAsString(event);
        VideoProcessingEvent restored = objectMapper.readValue(json, VideoProcessingEvent.class);

        assertThat(restored).isEqualTo(event);
    }

    @Test
    void shouldSerializeAndDeserializeVideoStatusEvent() throws Exception {
        VideoStatusEvent event = new VideoStatusEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@fiapx.com",
                "ERROR",
                null,
                "ffmpeg failed",
                LocalDateTime.of(2026, 8, 22, 11, 45)
        );

        String json = objectMapper.writeValueAsString(event);
        VideoStatusEvent restored = objectMapper.readValue(json, VideoStatusEvent.class);

        assertThat(restored).isEqualTo(event);
    }
}
