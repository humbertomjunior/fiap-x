package br.com.fiapx.auth.service;

import br.com.fiapx.auth.domain.User;
import br.com.fiapx.auth.dto.AuthResponseDTO;
import br.com.fiapx.auth.dto.LoginRequestDTO;
import br.com.fiapx.auth.dto.RegisterRequestDTO;
import br.com.fiapx.auth.dto.UserResponseDTO;
import br.com.fiapx.auth.repository.UserRepository;
import br.com.fiapx.auth.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldRegisterUserEncodingPassword() {
        RegisterRequestDTO request = new RegisterRequestDTO("User", "user@fiapx.com", "Password123");
        UUID userId = UUID.randomUUID();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setId(user, userId);
            return user;
        });

        UserResponseDTO response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getName()).isEqualTo("User");
        assertThat(savedUser.getEmail()).isEqualTo("user@fiapx.com");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(response).isEqualTo(new UserResponseDTO(userId, "User", "user@fiapx.com"));
    }

    @Test
    void shouldRejectDuplicateEmailOnRegister() {
        RegisterRequestDTO request = new RegisterRequestDTO("User", "user@fiapx.com", "Password123");
        User existing = new User();
        existing.setEmail(request.email());
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldLoginAndReturnJwt() {
        LoginRequestDTO request = new LoginRequestDTO("user@fiapx.com", "Password123");
        User user = new User();
        user.setEmail(request.email());
        user.setPassword("encoded-password");
        setId(user, UUID.randomUUID());
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
        when(jwtTokenService.generateToken(user)).thenReturn("jwt-token");
        when(jwtTokenService.getExpirationInSeconds()).thenReturn(7200L);

        AuthResponseDTO response = authService.login(request);

        assertThat(response).isEqualTo(new AuthResponseDTO("jwt-token", "Bearer", 7200L));
    }

    @Test
    void shouldRejectUnknownEmailOnLogin() {
        LoginRequestDTO request = new LoginRequestDTO("user@fiapx.com", "Password123");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void shouldRejectInvalidPasswordOnLogin() {
        LoginRequestDTO request = new LoginRequestDTO("user@fiapx.com", "wrong-password");
        User user = new User();
        user.setEmail(request.email());
        user.setPassword("encoded-password");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    private void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
