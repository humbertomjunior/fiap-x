package br.com.fiapx.auth.service;

import br.com.fiapx.auth.domain.User;
import br.com.fiapx.auth.dto.AuthResponseDTO;
import br.com.fiapx.auth.dto.LoginRequestDTO;
import br.com.fiapx.auth.dto.RegisterRequestDTO;
import br.com.fiapx.auth.dto.UserResponseDTO;
import br.com.fiapx.auth.metrics.AuthMetrics;
import br.com.fiapx.auth.repository.UserRepository;
import br.com.fiapx.auth.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthMetrics metrics;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuthMetrics metrics) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.metrics = metrics;
    }

    public UserResponseDTO register(RegisterRequestDTO request) {
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            metrics.recordRegistrationFailure();
            throw new DuplicateEmailException("Email already registered");
        });

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));

        User savedUser = userRepository.save(user);
        metrics.recordRegistrationSuccess();
        return new UserResponseDTO(savedUser.getId(), savedUser.getName(), savedUser.getEmail());
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    metrics.recordLoginFailure();
                    return new InvalidCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            metrics.recordLoginFailure();
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtTokenService.generateToken(user);
        metrics.recordLoginSuccess();
        return new AuthResponseDTO(token, "Bearer", jwtTokenService.getExpirationInSeconds());
    }
}