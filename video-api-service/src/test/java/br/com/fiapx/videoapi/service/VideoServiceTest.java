package br.com.fiapx.videoapi.service;

import br.com.fiapx.common.event.VideoProcessingEvent;
import br.com.fiapx.videoapi.config.StorageProperties;
import br.com.fiapx.videoapi.domain.Video;
import br.com.fiapx.videoapi.domain.VideoStatus;
import br.com.fiapx.videoapi.dto.VideoUploadResponseDTO;
import br.com.fiapx.videoapi.repository.VideoRepository;
import br.com.fiapx.videoapi.security.AuthenticatedUser;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldUploadVideoPersistingPendingStatusAndPublishingEvent() throws Exception {
        VideoRepository repository = mock(VideoRepository.class);
        SqsTemplate sqsTemplate = mock(SqsTemplate.class);
        Environment environment = mock(Environment.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");
        MockMultipartFile file = new MockMultipartFile("file", "../video.mp4", "video/mp4", "video".getBytes());
        UUID videoId = UUID.randomUUID();
        when(repository.save(any(Video.class))).thenAnswer(invocation -> {
            Video video = invocation.getArgument(0);
            setField(video, "id", videoId);
            return video;
        });

        VideoService service = new VideoService(repository, sqsTemplate, storage(tempDir), environment);

        VideoUploadResponseDTO response = service.uploadVideo(user, file, "  My title  ");

        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(repository).save(videoCaptor.capture());
        Video savedVideo = videoCaptor.getValue();
        assertThat(savedVideo.getStatus()).isEqualTo(VideoStatus.PENDING);
        assertThat(savedVideo.getTitle()).isEqualTo("My title");
        assertThat(savedVideo.getOriginalFileName()).isEqualTo("video.mp4");
        assertThat(Files.exists(Path.of(savedVideo.getOriginalStoragePath()))).isTrue();
        assertThat(response.status()).isEqualTo(VideoStatus.PENDING);
        assertThat(response.id()).isEqualTo(videoId);

        ArgumentCaptor<VideoProcessingEvent> eventCaptor = ArgumentCaptor.forClass(VideoProcessingEvent.class);
        verify(sqsTemplate).send(any());
    }

    @Test
    void shouldRejectUnsupportedExtension() {
        VideoService service = new VideoService(mock(VideoRepository.class), mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        assertThatThrownBy(() -> service.uploadVideo(
                new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com"),
                new MockMultipartFile("file", "video.mov", "video/quicktime", "x".getBytes()),
                null))
                .isInstanceOf(InvalidVideoException.class)
                .hasMessageContaining("Unsupported video format");
    }

    @Test
    void shouldRejectEmptyFile() {
        VideoService service = new VideoService(mock(VideoRepository.class), mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        assertThatThrownBy(() -> service.uploadVideo(
                new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com"),
                new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[0]),
                null))
                .isInstanceOf(InvalidVideoException.class)
                .hasMessage("Uploaded file must not be empty");
    }

    @Test
    void shouldListVideosForUser() {
        VideoRepository repository = mock(VideoRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");
        Video video = new Video();
        setField(video, "id", UUID.randomUUID());
        setField(video, "createdAt", LocalDateTime.now());
        video.setTitle("Video");
        video.setStatus(VideoStatus.FINISHED);
        video.setZipStoragePath("/tmp/video.zip");
        when(repository.findByUserIdOrderByCreatedAtDesc(user.userId())).thenReturn(List.of(video));

        VideoService service = new VideoService(repository, mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        assertThat(service.listVideos(user)).hasSize(1);
    }

    @Test
    void shouldDownloadZipWhenOwnershipAndStatusAreValid() throws Exception {
        VideoRepository repository = mock(VideoRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");
        UUID videoId = UUID.randomUUID();
        Path zipPath = Files.createFile(tempDir.resolve("video.zip"));
        Video video = new Video();
        video.setUserId(user.userId());
        video.setStatus(VideoStatus.FINISHED);
        video.setZipStoragePath(zipPath.toString());
        when(repository.findByIdAndUserId(videoId, user.userId())).thenReturn(Optional.of(video));

        VideoService service = new VideoService(repository, mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));
        Resource resource = service.downloadZip(user, videoId);

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getFilename()).isEqualTo("video.zip");
    }

    @Test
    void shouldRejectDownloadWhenVideoBelongsToAnotherUser() {
        VideoRepository repository = mock(VideoRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");
        UUID videoId = UUID.randomUUID();
        when(repository.findByIdAndUserId(videoId, user.userId())).thenReturn(Optional.empty());

        VideoService service = new VideoService(repository, mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        assertThatThrownBy(() -> service.downloadZip(user, videoId))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessage("Video not found");
    }

    @Test
    void shouldRejectDownloadWhenVideoIsNotFinished() {
        VideoRepository repository = mock(VideoRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");
        UUID videoId = UUID.randomUUID();
        Video video = new Video();
        video.setUserId(user.userId());
        video.setStatus(VideoStatus.PROCESSING);
        when(repository.findByIdAndUserId(videoId, user.userId())).thenReturn(Optional.of(video));

        VideoService service = new VideoService(repository, mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        assertThatThrownBy(() -> service.downloadZip(user, videoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Video is not available for download");
    }

    @Test
    void shouldRejectDownloadWhenZipPathIsMissing() {
        VideoRepository repository = mock(VideoRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");
        UUID videoId = UUID.randomUUID();
        Video video = new Video();
        video.setUserId(user.userId());
        video.setStatus(VideoStatus.FINISHED);
        when(repository.findByIdAndUserId(videoId, user.userId())).thenReturn(Optional.of(video));

        VideoService service = new VideoService(repository, mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        assertThatThrownBy(() -> service.downloadZip(user, videoId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Processed ZIP file path is missing");
    }

    @Test
    void shouldRejectDownloadWhenZipFileDoesNotExist() {
        VideoRepository repository = mock(VideoRepository.class);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");
        UUID videoId = UUID.randomUUID();
        Video video = new Video();
        video.setUserId(user.userId());
        video.setStatus(VideoStatus.FINISHED);
        video.setZipStoragePath(tempDir.resolve("missing.zip").toString());
        when(repository.findByIdAndUserId(videoId, user.userId())).thenReturn(Optional.of(video));

        VideoService service = new VideoService(repository, mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        assertThatThrownBy(() -> service.downloadZip(user, videoId))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessage("Processed ZIP file not found");
    }

    @Test
    void shouldUpdateStatusAndMetadata() {
        VideoRepository repository = mock(VideoRepository.class);
        Video video = new Video();
        video.setUserId(UUID.randomUUID());
        video.setUserEmail("user@fiapx.com");
        video.setTitle("video");
        video.setOriginalFileName("video.mp4");
        video.setOriginalStoragePath("/tmp/video.mp4");
        video.setStatus(VideoStatus.PENDING);

        UUID videoId = UUID.randomUUID();
        when(repository.findById(videoId)).thenReturn(Optional.of(video));

        VideoService service = new VideoService(repository, mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        service.updateVideoStatus(videoId, VideoStatus.FINISHED, "/tmp/video.zip", null);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.FINISHED);
        assertThat(video.getZipStoragePath()).isEqualTo("/tmp/video.zip");
        assertThat(video.getErrorMessage()).isNull();
    }

    @Test
    void shouldThrowWhenVideoDoesNotExistDuringStatusUpdate() {
        VideoRepository repository = mock(VideoRepository.class);
        UUID videoId = UUID.randomUUID();
        when(repository.findById(videoId)).thenReturn(Optional.empty());

        VideoService service = new VideoService(repository, mock(SqsTemplate.class), storage(tempDir), mock(Environment.class));

        assertThatThrownBy(() -> service.updateVideoStatus(videoId, VideoStatus.ERROR, null, "failure"))
                .isInstanceOf(VideoNotFoundException.class)
                .hasMessage("Video not found");
    }

    private StorageProperties storage(Path root) {
        return new StorageProperties(root.resolve("uploads").toString(), root.resolve("frames").toString(), root.resolve("zips").toString());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
