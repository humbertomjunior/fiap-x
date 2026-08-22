package br.com.fiapx.auth.controller;

import br.com.fiapx.auth.dto.AuthResponseDTO;
import br.com.fiapx.auth.dto.LoginRequestDTO;
import br.com.fiapx.auth.dto.RegisterRequestDTO;
import br.com.fiapx.auth.dto.UserResponseDTO;
import br.com.fiapx.auth.security.JwtAuthenticationFilter;
import br.com.fiapx.auth.security.JwtTokenService;
import br.com.fiapx.auth.security.SecurityConfig;
import br.com.fiapx.auth.service.AuthService;
import br.com.fiapx.auth.service.DuplicateEmailException;
import br.com.fiapx.auth.service.InvalidCredentialsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenService jwtTokenService;

    @Test
    void shouldRegisterUser() throws Exception {
        UserResponseDTO response = new UserResponseDTO(UUID.randomUUID(), "User", "user@fiapx.com");
        when(authService.register(any(RegisterRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequestDTO("User", "user@fiapx.com", "Password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.email").value("user@fiapx.com"));
    }

    @Test
    void shouldRejectInvalidRegisterPayload() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequestDTO("", "bad-email", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void shouldReturnConflictWhenEmailAlreadyExists() throws Exception {
        when(authService.register(any(RegisterRequestDTO.class)))
                .thenThrow(new DuplicateEmailException("Email already registered"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequestDTO("User", "user@fiapx.com", "Password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email already registered"));
    }

    @Test
    void shouldLogin() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenReturn(new AuthResponseDTO("jwt-token", "Bearer", 7200L));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO("user@fiapx.com", "Password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(7200));
    }

    @Test
    void shouldRejectInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO("user@fiapx.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void shouldValidateTokenFromQueryParam() throws Exception {
        when(jwtTokenService.validateToken("jwt-token")).thenReturn(true);

        mockMvc.perform(get("/auth/validate").param("token", "jwt-token"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldValidateTokenFromAuthorizationHeader() throws Exception {
        when(jwtTokenService.validateToken("jwt-token")).thenReturn(true);
        Claims claims = mock(Claims.class);
        when(claims.get("email")).thenReturn("user@fiapx.com");
        when(jwtTokenService.getClaims("jwt-token")).thenReturn(claims);

        mockMvc.perform(get("/auth/validate").header(HttpHeaders.AUTHORIZATION, "Bearer jwt-token"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectMissingTokenOnValidate() throws Exception {
        mockMvc.perform(get("/auth/validate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectExpiredOrInvalidTokenOnValidate() throws Exception {
        when(jwtTokenService.validateToken("expired-token")).thenReturn(false);

        mockMvc.perform(get("/auth/validate").param("token", "expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Invalid or expired token"));
    }
}
