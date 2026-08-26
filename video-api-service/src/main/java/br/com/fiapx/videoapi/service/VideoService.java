package br.com.fiapx.videoapi.service;

import br.com.fiapx.common.config.SqsQueueNames;
import br.com.fiapx.common.event.VideoProcessingEvent;
import br.com.fiapx.videoapi.config.StorageProperties;
import br.com.fiapx.videoapi.domain.Video;
import br.com.fiapx.videoapi.domain.VideoStatus;
import br.com.fiapx.videoapi.dto.VideoResponseDTO;
import br.com.fiapx.videoapi.dto.VideoUploadResponseDTO;
import br.com.fiapx.videoapi.metrics.VideoApiMetrics;
import br.com.fiapx.videoapi.repository.VideoRepository;
import br.com.fiapx.videoapi.security.AuthenticatedUser;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VideoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoService.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".mp4", ".avi", ".mkv");

    private final VideoRepository videoRepository;
    private final SqsTemplate sqsTemplate;
    private final Path uploadRoot;
    private final Environment environment;
    private final VideoApiMetrics metrics;

    public VideoService(
            VideoRepository videoRepository,
            SqsTemplate sqsTemplate,
            StorageProperties storageProperties,
            Environment environment,
            VideoApiMetrics metrics) {
        this.videoRepository = videoRepository;
        this.sqsTemplate = sqsTemplate;
        this.uploadRoot = Path.of(storageProperties.uploadDir()).toAbsolutePath().normalize();
        this.environment = environment;
        this.metrics = metrics;
    }

    @Transactional
    public VideoUploadResponseDTO uploadVideo(AuthenticatedUser user, MultipartFile file, String title) {
        Timer.Sample sample = metrics.startUploadTimer();
        try {
            validateFile(file);
            String originalFilename = sanitizeFilename(file.getOriginalFilename());
            String resolvedTitle = StringUtils.hasText(title) ? title.trim() : stripExtension(originalFilename);
            Path storedFile = storeOriginalFile(file, user.userId(), originalFilename);

            Video video = new Video();
            video.setUserId(user.userId());
            video.setUserEmail(user.email());
            video.setTitle(resolvedTitle);
            video.setOriginalFileName(originalFilename);
            video.setOriginalStoragePath(storedFile.toString());
            video.setStatus(VideoStatus.PENDING);

            Video savedVideo = videoRepository.save(video);
            publishVideoUploaded(savedVideo);

            metrics.recordUploadSuccess(file.getSize());

            return new VideoUploadResponseDTO(
                    savedVideo.getId(),
                    savedVideo.getTitle(),
                    savedVideo.getStatus(),
                    "Video uploaded successfully and queued for processing"
            );
        } catch (RuntimeException ex) {
            metrics.recordUploadFailure();
            throw ex;
        } finally {
            metrics.stopUploadTimer(sample);
        }
    }

    @Transactional(readOnly = true)
    public List<VideoResponseDTO> listVideos(AuthenticatedUser user) {
        return videoRepository.findByUserIdOrderByCreatedAtDesc(user.userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource downloadZip(AuthenticatedUser user, UUID videoId) {
        Video video = videoRepository.findByIdAndUserId(videoId, user.userId())
                .orElseThrow(() -> new VideoNotFoundException("Video not found"));

        if (video.getStatus() != VideoStatus.FINISHED) {
            throw new IllegalStateException("Video is not available for download");
        }
        if (!StringUtils.hasText(video.getZipStoragePath())) {
            throw new IllegalStateException("Processed ZIP file path is missing");
        }

        Path zipPath = Path.of(video.getZipStoragePath()).toAbsolutePath().normalize();
        if (!Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            throw new VideoNotFoundException("Processed ZIP file not found");
        }

        return new PathResource(zipPath);
    }

    @Transactional
    public void updateVideoStatus(UUID videoId, VideoStatus status, String zipStoragePath, String errorMessage) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new VideoNotFoundException("Video not found"));

        video.setStatus(status);
        video.setZipStoragePath(zipStoragePath);
        video.setErrorMessage(errorMessage);
    }

    private VideoResponseDTO toResponse(Video video) {
        return new VideoResponseDTO(
                video.getId(),
                video.getTitle(),
                video.getStatus(),
                video.getZipStoragePath(),
                video.getCreatedAt()
        );
    }

    private void publishVideoUploaded(Video video) {
        VideoProcessingEvent event = new VideoProcessingEvent(
                video.getId(),
                video.getUserId(),
                video.getUserEmail(),
                video.getTitle(),
                video.getOriginalStoragePath(),
                LocalDateTime.now()
        );

        logLocalSqsSend(SqsQueueNames.VIDEO_UPLOADED_QUEUE, event);
        sqsTemplate.send(to -> to.queue(SqsQueueNames.VIDEO_UPLOADED_QUEUE).payload(event));
    }

    private void logLocalSqsSend(String queueName, Object payload) {
        if (isLocalProfileActive()) {
            LOGGER.info("[LOCAL SQS SEND] queue={} payload={}", queueName, payload);
        }
    }

    private boolean isLocalProfileActive() {
        return environment.matchesProfiles("local");
    }

    private Path storeOriginalFile(MultipartFile file, UUID userId, String originalFilename) {
        String extension = getExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + extension;
        Path targetDirectory = uploadRoot.resolve(userId.toString());
        Path targetFile = targetDirectory.resolve(storedFilename);

        try {
            Files.createDirectories(targetDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return targetFile;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to store uploaded file", ex);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidVideoException("Uploaded file must not be empty");
        }
        String filename = sanitizeFilename(file.getOriginalFilename());
        String extension = getExtension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new InvalidVideoException("Unsupported video format. Supported formats: .mp4, .avi, .mkv");
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new InvalidVideoException("Original filename is required");
        }
        String sanitized = Path.of(originalFilename).getFileName().toString();
        if (!StringUtils.hasText(sanitized)) {
            throw new InvalidVideoException("Original filename is invalid");
        }
        return sanitized;
    }

    private String getExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return filename.substring(index);
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index > 0 ? filename.substring(0, index) : filename;
    }
}