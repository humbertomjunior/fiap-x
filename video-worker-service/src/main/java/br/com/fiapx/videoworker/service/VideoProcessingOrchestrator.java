package br.com.fiapx.videoworker.service;

import br.com.fiapx.common.config.SqsQueueNames;
import br.com.fiapx.common.event.VideoProcessingEvent;
import br.com.fiapx.common.event.VideoStatusEvent;
import br.com.fiapx.videoworker.config.ProcessingProperties;
import br.com.fiapx.videoworker.domain.Video;
import br.com.fiapx.videoworker.domain.VideoStatus;
import br.com.fiapx.videoworker.repository.VideoRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final VideoRepository videoRepository;
    private final FfmpegService ffmpegService;
    private final ZipService zipService;
    private final SqsTemplate sqsTemplate;
    private final Path framesRoot;
    private final Environment environment;

    public VideoProcessingOrchestrator(
            VideoRepository videoRepository,
            FfmpegService ffmpegService,
            ZipService zipService,
            SqsTemplate sqsTemplate,
            ProcessingProperties processingProperties,
            Environment environment) {
        this.videoRepository = videoRepository;
        this.ffmpegService = ffmpegService;
        this.zipService = zipService;
        this.sqsTemplate = sqsTemplate;
        this.framesRoot = Path.of(processingProperties.storage().framesDir()).toAbsolutePath().normalize();
        this.environment = environment;
    }

    public void process(VideoProcessingEvent event) {
        Path framesDirectory = framesRoot.resolve(safeUserDirectory(event.userId())).resolve(event.videoId().toString());
        Video video = videoRepository.findById(event.videoId())
                .orElseThrow(() -> new VideoNotFoundException("Video not found: " + event.videoId()));

        try {
            markProcessing(video);
            ffmpegService.extractFrames(Path.of(event.originalStoragePath()).toAbsolutePath().normalize(), framesDirectory);
            Path zipPath = zipService.createZip(event.videoId(), framesDirectory);
            markFinished(video, zipPath);
            publishStatus(video, VideoStatus.FINISHED, zipPath, null);
        } catch (Exception ex) {
<<<<<<< Updated upstream
            markError(event.videoId(), ex);
            publishStatus(event, truncateError(ex.getMessage()));
=======
            publishStatus(event, VideoStatus.ERROR, null, truncateError(ex.getMessage()));
            throw ex instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException("Video processing failed", ex);
>>>>>>> Stashed changes
        } finally {
            deleteFramesDirectory(framesDirectory);
        }
    }

    @Transactional
    protected void markProcessing(Video video) {
        video.setStatus(VideoStatus.PROCESSING);
        video.setErrorMessage(null);
        videoRepository.save(video);
    }

    @Transactional
    protected void markFinished(Video video, Path zipPath) {
        video.setStatus(VideoStatus.FINISHED);
        video.setZipStoragePath(zipPath.toString());
        video.setErrorMessage(null);
        videoRepository.save(video);
    }

    @Transactional
    protected void markError(UUID videoId, Exception ex) {
        videoRepository.findById(videoId).ifPresent(video -> {
            video.setStatus(VideoStatus.ERROR);
            video.setErrorMessage(truncateError(ex.getMessage()));
            videoRepository.save(video);
        });
    }

    private void publishStatus(Video video, VideoStatus status, Path zipPath, String errorMessage) {
        VideoStatusEvent event = new VideoStatusEvent(
                video.getId(),
                video.getUserId(),
                video.getUserEmail(),
                status.name(),
                zipPath != null ? zipPath.toString() : null,
                errorMessage,
                LocalDateTime.now()
        );
        logLocalSqsSend(SqsQueueNames.VIDEO_STATUS_QUEUE, event);
        sqsTemplate.send(to -> to.queue(SqsQueueNames.VIDEO_STATUS_QUEUE).payload(event));
    }

    private void publishStatus(VideoProcessingEvent event, String errorMessage) {
        VideoStatusEvent statusEvent = new VideoStatusEvent(
                event.videoId(),
                event.userId(),
                event.userEmail(),
                VideoStatus.ERROR.name(),
                null,
                errorMessage,
                LocalDateTime.now()
        );
        logLocalSqsSend(SqsQueueNames.VIDEO_STATUS_QUEUE, statusEvent);
        sqsTemplate.send(to -> to.queue(SqsQueueNames.VIDEO_STATUS_QUEUE).payload(statusEvent));
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
