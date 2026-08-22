package br.com.fiapx.videoapi.controller;

import br.com.fiapx.videoapi.dto.VideoResponseDTO;
import br.com.fiapx.videoapi.security.AuthenticatedUser;
import br.com.fiapx.videoapi.security.JwtAuthenticationFilter;
import br.com.fiapx.videoapi.security.JwtTokenService;
import br.com.fiapx.videoapi.security.SecurityConfig;
import br.com.fiapx.videoapi.service.VideoNotFoundException;
import br.com.fiapx.videoapi.service.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VideoController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class})
class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VideoService videoService;

    @MockBean
    private JwtTokenService jwtTokenService;

    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "user@fiapx.com");

    private UsernamePasswordAuthenticationToken authenticationToken() {
        return new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.NO_AUTHORITIES);
    }

    @Test
    void shouldRequireAuthenticationToListVideos() throws Exception {
        mockMvc.perform(get("/videos"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldListVideosForAuthenticatedUser() throws Exception {
        when(videoService.listVideos(user)).thenReturn(List.of(
                new VideoResponseDTO(UUID.randomUUID(), "Video 1", br.com.fiapx.videoapi.domain.VideoStatus.FINISHED, "/tmp/video.zip", LocalDateTime.now())
        ));

        mockMvc.perform(get("/videos").with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Video 1"))
                .andExpect(jsonPath("$[0].status").value("FINISHED"));
    }

    @Test
    void shouldDownloadProcessedZip() throws Exception {
        UUID videoId = UUID.randomUUID();
        ByteArrayResource resource = new ByteArrayResource("zip".getBytes()) {
            @Override
            public String getFilename() {
                return "processed.zip";
            }
        };
        when(videoService.downloadZip(user, videoId)).thenReturn(resource);

        mockMvc.perform(get("/videos/{id}/download", videoId).with(authentication(authenticationToken())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("processed.zip")))
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_OCTET_STREAM_VALUE));
    }

    @Test
    void shouldReturnNotFoundWhenOwnershipIsInvalid() throws Exception {
        UUID videoId = UUID.randomUUID();
        when(videoService.downloadZip(user, videoId)).thenThrow(new VideoNotFoundException("Video not found"));

        mockMvc.perform(get("/videos/{id}/download", videoId).with(authentication(authenticationToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Video not found"));
    }

    @Test
    void shouldReturnBadRequestWhenVideoStatusIsNotFinished() throws Exception {
        UUID videoId = UUID.randomUUID();
        when(videoService.downloadZip(user, videoId)).thenThrow(new IllegalStateException("Video is not available for download"));

        mockMvc.perform(get("/videos/{id}/download", videoId).with(authentication(authenticationToken())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Video is not available for download"));
    }
}
