package br.com.fiapx.videoworker.service;

import br.com.fiapx.common.config.SqsQueueNames;
import br.com.fiapx.common.event.VideoProcessingEvent;
import br.com.fiapx.common.event.VideoStatusEvent;
import br.com.fiapx.videoworker.config.ProcessingProperties;
import br.com.fiapx.videoworker.domain.VideoStatus;
import br.com.fiapx.videoworker.metrics.VideoProcessingMetrics;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class VideoProcessingOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoProcessingOrchestrator.class);

    private final FfmpegService ffmpegService;
    private final ZipService zipService;
    private final SqsTemplate sqsTemplate;
    private final Path framesRoot;
    private final Environment environment;
    private final VideoProcessingMetrics metrics;

    public VideoProcessingOrchestrator(
            FfmpegService ffmpegService,
            ZipService zipService,
            SqsTemplate sqsTemplate,
            ProcessingProperties processingProperties,
            Environment environment,
            VideoProcessingMetrics metrics) {
        this.ffmpegService = ffmpegService;
        this.zipService = zipService;
        this.sqsTemplate = sqsTemplate;
        this.framesRoot = Path.of(processingProperties.storage().framesDir()).toAbsolutePath().normalize();
        this.environment = environment;
        this.metrics = metrics;
    }

    public void process(VideoProcessingEvent event) {
        Path framesDirectory = framesRoot.resolve(safeUserDirectory(event.userId())).resolve(event.videoId().toString());
        Timer.Sample sample = metrics.startTimer();

        try {
            publishStatus(event, VideoStatus.PROCESSING, null, null);

            ffmpegService.extractFrames(Path.of(event.originalStoragePath()).toAbsolutePath().normalize(), framesDirectory);
            metrics.recordFramesExtracted(countExtractedFrames(framesDirectory));

            Path zipPath = zipService.createZip(event.videoId(), framesDirectory);
            publishStatus(event, VideoStatus.FINISHED, zipPath, null);
            metrics.recordSuccess();

        } catch (NonRetryableVideoProcessingException ex) {
            publishStatus(event, VideoStatus.ERROR, null, truncateError(ex.getMessage()));
            metrics.recordFailure();
        } catch (Exception ex) {
            publishStatus(event, VideoStatus.ERROR, null, truncateError(ex.getMessage()));
            metrics.recordFailure();
            throw ex instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException("Video processing failed", ex);
        } finally {
            metrics.stopTimer(sample);
            deleteFramesDirectory(framesDirectory);
        }
    }

    private int countExtractedFrames(Path framesDirectory) {
        if (Files.notExists(framesDirectory)) {
            return 0;
        }
        try (Stream<Path> pathStream = Files.list(framesDirectory)) {
            return (int) pathStream.filter(Files::isRegularFile).count();
        } catch (IOException ex) {
            LOGGER.warn("Falha ao contar frames extraidos em: {}", framesDirectory, ex);
            return 0;
        }
    }

    private void publishStatus(VideoProcessingEvent sourceEvent, VideoStatus status, Path zipPath, String errorMessage) {
        VideoStatusEvent event = new VideoStatusEvent(
                sourceEvent.videoId(),
                sourceEvent.userId(),
                sourceEvent.userEmail(),
                status.name(),
                zipPath != null ? zipPath.toString() : null,
                errorMessage,
                LocalDateTime.now()
        );
        sendStatusEvent(SqsQueueNames.VIDEO_STATUS_API_QUEUE, event);
        sendStatusEvent(SqsQueueNames.VIDEO_STATUS_NOTIFICATION_QUEUE, event);
    }

    private void sendStatusEvent(String queueName, VideoStatusEvent event) {
        logLocalSqsSend(queueName, event);
        sqsTemplate.send(to -> to.queue(queueName).payload(event));
    }

    private void logLocalSqsSend(String queueName, Object payload) {
        if (environment.matchesProfiles("local")) {
            LOGGER.info("[LOCAL SQS SEND] queue={} payload={}", queueName, payload);
        }
    }

    private String safeUserDirectory(UUID userId) {
        return userId.toString();
    }

    private void deleteFramesDirectory(Path framesDirectory) {
        if (Files.notExists(framesDirectory)) {
            return;
        }

        try (Stream<Path> pathStream = Files.walk(framesDirectory)) {
            pathStream
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            LOGGER.warn("Falha ao deletar arquivo temporario de frames: {}", path, ex);
                        }
                    });
        } catch (IOException ex) {
            LOGGER.warn("Falha ao percorrer diretorio temporario de frames: {}", framesDirectory, ex);
        }
    }

    private String truncateError(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown processing error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}