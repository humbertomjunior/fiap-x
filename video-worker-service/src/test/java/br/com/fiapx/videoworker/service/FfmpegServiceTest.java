package br.com.fiapx.videoworker.service;

import br.com.fiapx.videoworker.config.ProcessingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FfmpegServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteFfmpegCommand() throws Exception {
        Path fakeFfmpeg = createExecutable("fake-ffmpeg-success.sh", """
                #!/bin/sh
                output="$5"
                touch "$output"
                exit 0
                """);
        FfmpegService service = new FfmpegService(properties(tempDir, fakeFfmpeg.toString()));
        Path input = Files.createFile(tempDir.resolve("video.mp4"));
        Path output = tempDir.resolve("frames");

        service.extractFrames(input, output);

        assertThat(Files.isDirectory(output)).isTrue();
        assertThat(Files.exists(output.resolve("frame_%04d.png"))).isTrue();
    }

    @Test
    void shouldFailWhenFfmpegReturnsNonZeroExitCode() throws Exception {
        Path fakeFfmpeg = createExecutable("fake-ffmpeg-failure.sh", """
                #!/bin/sh
                echo "corrupted file"
                exit 1
                """);
        FfmpegService service = new FfmpegService(properties(tempDir, fakeFfmpeg.toString()));
        Path input = Files.createFile(tempDir.resolve("video.mp4"));

        assertThatThrownBy(() -> service.extractFrames(input, tempDir.resolve("frames")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FFmpeg failed with exit code 1")
                .hasMessageContaining("corrupted file");
    }

    @Test
    void shouldFailWhenProcessStartThrowsIOException() {
        FfmpegService service = new FfmpegService(properties(tempDir, tempDir.resolve("missing-ffmpeg").toString()));

        assertThatThrownBy(() -> service.extractFrames(tempDir.resolve("video.mp4"), tempDir.resolve("frames")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to execute FFmpeg");
    }

    @Test
    void shouldRestoreInterruptedFlagWhenInterrupted() throws Exception {
        Path fakeFfmpeg = createExecutable("fake-ffmpeg-sleep.sh", """
                #!/bin/sh
                sleep 5
                """);
        FfmpegService service = new FfmpegService(properties(tempDir, fakeFfmpeg.toString()));
        Path input = Files.createFile(tempDir.resolve("video.mp4"));

        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> service.extractFrames(input, tempDir.resolve("frames")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("FFmpeg execution was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private ProcessingProperties properties(Path root, String binaryPath) {
        return new ProcessingProperties(
                new ProcessingProperties.Ffmpeg(binaryPath),
                new ProcessingProperties.Storage(root.resolve("uploads").toString(), root.resolve("frames").toString(), root.resolve("zips").toString())
        );
    }

    private Path createExecutable(String name, String content) throws Exception {
        Path script = tempDir.resolve(name);
        Files.writeString(script, content);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        return script;
    }
}
