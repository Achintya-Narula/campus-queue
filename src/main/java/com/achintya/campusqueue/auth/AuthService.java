package com.achintya.campusqueue.auth;

import com.achintya.campusqueue.auth.dto.AuthResponse;
import com.achintya.campusqueue.auth.dto.LoginRequest;
import com.achintya.campusqueue.auth.dto.RegisterRequest;
import com.achintya.campusqueue.auth.dto.UserResponse;
import com.achintya.campusqueue.common.error.ApiException;
import com.achintya.campusqueue.common.error.ConflictException;
import com.achintya.campusqueue.common.security.JwtService;
import com.achintya.campusqueue.user.UserEntity;
import com.achintya.campusqueue.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw emailAlreadyExists();
        }
        UserEntity user = new UserEntity(
                email,
                passwordEncoder.encode(request.password()),
                request.role(),
                Instant.now(clock));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw emailAlreadyExists();
        }
        return responseFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS",
                    "Email or password is incorrect");
        }
        return responseFor(user);
    }

    private AuthResponse responseFor(UserEntity user) {
        return AuthResponse.bearer(jwtService.issueToken(user), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ConflictException emailAlreadyExists() {
        return new ConflictException(
                "EMAIL_ALREADY_EXISTS",
                "An account with this email already exists");
    }
}
