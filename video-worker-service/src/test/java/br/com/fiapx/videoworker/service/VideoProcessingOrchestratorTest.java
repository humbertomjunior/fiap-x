package br.com.fiapx.videoworker.service;

import br.com.fiapx.common.event.VideoProcessingEvent;
import br.com.fiapx.common.event.VideoStatusEvent;
import br.com.fiapx.videoworker.config.ProcessingProperties;
import br.com.fiapx.videoworker.metrics.VideoProcessingMetrics;
import io.awspring.cloud.sqs.operations.SqsSendOptions;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VideoProcessingOrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldProcessVideoAndCleanupFramesDirectory() throws Exception {
        FfmpegService ffmpegService = mock(FfmpegService.class);
        ZipService zipService = mock(ZipService.class);
        SqsTemplate sqsTemplate = mock(SqsTemplate.class);
        Environment environment = mock(Environment.class);
        VideoProcessingMetrics metrics = mock(VideoProcessingMetrics.class);
        VideoProcessingOrchestrator orchestrator = new VideoProcessingOrchestrator(
                ffmpegService, zipService, sqsTemplate, properties(tempDir), environment, metrics);
        VideoProcessingEvent event = event();
        Path framesDir = tempDir.resolve("frames").resolve(event.userId().toString()).resolve(event.videoId().toString());
        Path zipPath = Files.createFile(tempDir.resolve("output.zip"));

        org.mockito.Mockito.doAnswer(invocation -> {
            Files.createDirectories(framesDir);
            Files.writeString(framesDir.resolve("frame_0001.png"), "frame");
            return null;
        }).when(ffmpegService).extractFrames(Path.of(event.originalStoragePath()).toAbsolutePath().normalize(), framesDir);
        org.mockito.Mockito.when(zipService.createZip(event.videoId(), framesDir)).thenReturn(zipPath);

        orchestrator.process(event);

        verify(ffmpegService).extractFrames(Path.of(event.originalStoragePath()).toAbsolutePath().normalize(), framesDir);
        verify(zipService).createZip(event.videoId(), framesDir);
        verify(sqsTemplate, org.mockito.Mockito.times(4)).send(any());
        assertThat(Files.exists(framesDir)).isFalse();
    }

    @Test
    void shouldPublishErrorOnceAndCleanupWhenBusinessFailureHappens() throws Exception {
        FfmpegService ffmpegService = mock(FfmpegService.class);
        ZipService zipService = mock(ZipService.class);
        SqsTemplate sqsTemplate = mock(SqsTemplate.class);
        Environment environment = mock(Environment.class);
        VideoProcessingMetrics metrics = mock(VideoProcessingMetrics.class);
        VideoProcessingOrchestrator orchestrator = new VideoProcessingOrchestrator(
                ffmpegService, zipService, sqsTemplate, properties(tempDir), environment, metrics);
        VideoProcessingEvent event = event();
        Path framesDir = tempDir.resolve("frames").resolve(event.userId().toString()).resolve(event.videoId().toString());
        Files.createDirectories(framesDir);
        Files.writeString(framesDir.resolve("temp.txt"), "temp");
        doThrow(new NonRetryableVideoProcessingException("corrupted file")).when(ffmpegService)
                .extractFrames(Path.of(event.originalStoragePath()).toAbsolutePath().normalize(), framesDir);

        orchestrator.process(event);

        verify(sqsTemplate, org.mockito.Mockito.times(4)).send(any());
        List<VideoStatusEvent> statusEvents = capturedStatusEvents(sqsTemplate);
        assertThat(statusEvents)
                .extracting(VideoStatusEvent::status)
                .containsExactly("PROCESSING", "PROCESSING", "ERROR", "ERROR");
        assertThat(statusEvents)
                .filteredOn(statusEvent -> "ERROR".equals(statusEvent.status()))
                .extracting(VideoStatusEvent::errorMessage)
                .containsExactly("corrupted file", "corrupted file");
        verify(zipService, never()).createZip(event.videoId(), framesDir);
        assertThat(Files.exists(framesDir)).isFalse();
    }

    @Test
    void shouldRethrowAndCleanupWhenInfrastructureFailureHappens() throws Exception {
        FfmpegService ffmpegService = mock(FfmpegService.class);
        ZipService zipService = mock(ZipService.class);
        SqsTemplate sqsTemplate = mock(SqsTemplate.class);
        Environment environment = mock(Environment.class);
        VideoProcessingMetrics metrics = mock(VideoProcessingMetrics.class);
        VideoProcessingOrchestrator orchestrator = new VideoProcessingOrchestrator(
                ffmpegService, zipService, sqsTemplate, properties(tempDir), environment, metrics);
        VideoProcessingEvent event = event();
        Path framesDir = tempDir.resolve("frames").resolve(event.userId().toString()).resolve(event.videoId().toString());
        Files.createDirectories(framesDir);
        Files.writeString(framesDir.resolve("temp.txt"), "temp");
        doThrow(new IllegalStateException("disk full")).when(ffmpegService)
                .extractFrames(Path.of(event.originalStoragePath()).toAbsolutePath().normalize(), framesDir);

        assertThatThrownBy(() -> orchestrator.process(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disk full");

        verify(sqsTemplate, org.mockito.Mockito.times(4)).send(any());
        List<VideoStatusEvent> statusEvents = capturedStatusEvents(sqsTemplate);
        assertThat(statusEvents)
                .extracting(VideoStatusEvent::status)
                .containsExactly("PROCESSING", "PROCESSING", "ERROR", "ERROR");
        assertThat(Files.exists(framesDir)).isFalse();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<VideoStatusEvent> capturedStatusEvents(SqsTemplate sqsTemplate) {
        ArgumentCaptor<Consumer<SqsSendOptions<Object>>> captor = ArgumentCaptor.forClass((Class) Consumer.class);
        verify(sqsTemplate, org.mockito.Mockito.times(4)).send(captor.capture());
        List<VideoStatusEvent> events = new ArrayList<>();
        for (Consumer<SqsSendOptions<Object>> consumer : captor.getAllValues()) {
            SqsSendOptions<Object> options = mock(SqsSendOptions.class);
            org.mockito.Mockito.when(options.queue(any())).thenReturn(options);
            org.mockito.Mockito.when(options.payload(any())).thenAnswer(invocation -> {
                Object payload = invocation.getArgument(0);
                if (payload instanceof VideoStatusEvent statusEvent) {
                    events.add(statusEvent);
                }
                return options;
            });
            consumer.accept(options);
        }
        return events;
    }

    private ProcessingProperties properties(Path root) {
        return new ProcessingProperties(
                new ProcessingProperties.Ffmpeg("ffmpeg"),
                new ProcessingProperties.Storage(root.resolve("uploads").toString(), root.resolve("frames").toString(), root.resolve("zips").toString())
        );
    }

    private VideoProcessingEvent event() throws Exception {
        Path original = Files.createFile(tempDir.resolve("original.mp4"));
        return new VideoProcessingEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user@fiapx.com",
                "Video",
                original.toString(),
                LocalDateTime.now()
        );
    }
}