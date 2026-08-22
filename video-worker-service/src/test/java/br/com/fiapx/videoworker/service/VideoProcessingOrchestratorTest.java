package br.com.fiapx.videoworker.service;

import br.com.fiapx.common.event.VideoProcessingEvent;
import br.com.fiapx.videoworker.config.ProcessingProperties;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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
        VideoProcessingOrchestrator orchestrator = new VideoProcessingOrchestrator(
                ffmpegService, zipService, sqsTemplate, properties(tempDir), environment);
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
    void shouldPublishErrorAndCleanupWhenProcessingFails() throws Exception {
        FfmpegService ffmpegService = mock(FfmpegService.class);
        ZipService zipService = mock(ZipService.class);
        SqsTemplate sqsTemplate = mock(SqsTemplate.class);
        Environment environment = mock(Environment.class);
        VideoProcessingOrchestrator orchestrator = new VideoProcessingOrchestrator(
                ffmpegService, zipService, sqsTemplate, properties(tempDir), environment);
        VideoProcessingEvent event = event();
        Path framesDir = tempDir.resolve("frames").resolve(event.userId().toString()).resolve(event.videoId().toString());
        Files.createDirectories(framesDir);
        Files.writeString(framesDir.resolve("temp.txt"), "temp");
        doThrow(new IllegalStateException("corrupted file")).when(ffmpegService)
                .extractFrames(Path.of(event.originalStoragePath()).toAbsolutePath().normalize(), framesDir);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orchestrator.process(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("corrupted file");

        verify(sqsTemplate, org.mockito.Mockito.times(4)).send(any());
        assertThat(Files.exists(framesDir)).isFalse();
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
