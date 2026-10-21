package com.gobang.service;

import com.gobang.dto.ApiResponse;
import com.gobang.dto.UserResponse;
import com.gobang.entity.Score;
import com.gobang.entity.UserInfo;
import com.gobang.repository.ScoreRepository;
import com.gobang.repository.UserInfoRepository;
import com.gobang.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserInfoRepository userRepo;
    @Mock private ScoreRepository scoreRepo;
    @Mock private SecurityContextRepository securityContextRepository;
    @Mock private SessionAuthenticationStrategy sessionAuthenticationStrategy;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;

    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(
                userRepo,
                scoreRepo,
                passwordEncoder,
                securityContextRepository,
                sessionAuthenticationStrategy);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrationHashesPasswordAndReturnsOnlySafeUserData() {
        when(userRepo.existsByNickname("棋友")).thenReturn(false);
        when(userRepo.save(any(UserInfo.class))).thenAnswer(invocation -> {
            UserInfo user = invocation.getArgument(0);
            user.setId(12L);
            user.setCreatedAt(LocalDateTime.of(2026, 9, 21, 10, 0));
            return user;
        });

        ApiResponse<UserResponse> result = userService.register("  棋友  ", "secure-pass");

        ArgumentCaptor<UserInfo> userCaptor = ArgumentCaptor.forClass(UserInfo.class);
        verify(userRepo).save(userCaptor.capture());
        verify(scoreRepo).save(any(Score.class));
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().nickname()).isEqualTo("棋友");
        assertThat(result.getData().toString()).doesNotContain("secure-pass");
        assertThat(userCaptor.getValue().getPassword()).isNotEqualTo("secure-pass");
        assertThat(passwordEncoder.matches("secure-pass", userCaptor.getValue().getPassword())).isTrue();
    }

    @Test
    void loginPersistsAnAuthenticatedSpringSecurityContext() {
        UserInfo user = user(7L, "player", passwordEncoder.encode("secure-pass"));
        when(userRepo.findByNickname("player")).thenReturn(Optional.of(user));

        ApiResponse<UserResponse> result = userService.login("player", "secure-pass", request, response);

        verify(sessionAuthenticationStrategy).onAuthentication(any(), any(), any());
        verify(securityContextRepository).saveContext(any(SecurityContext.class), any(), any());
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().id()).isEqualTo(7L);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new AuthenticatedUser(7L, "player"));
    }

    @Test
    void successfulLegacyLoginUpgradesPlaintextPasswordToBcrypt() {
        UserInfo user = user(8L, "legacy", "old-password");
        when(userRepo.findByNickname("legacy")).thenReturn(Optional.of(user));
        when(userRepo.save(user)).thenReturn(user);

        ApiResponse<UserResponse> result = userService.login("legacy", "old-password", request, response);

        verify(userRepo).save(user);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(user.getPassword()).isNotEqualTo("old-password");
        assertThat(passwordEncoder.matches("old-password", user.getPassword())).isTrue();
    }

    @Test
    void invalidCredentialsDoNotCreateAnAuthenticatedSession() {
        UserInfo user = user(9L, "player", passwordEncoder.encode("correct-password"));
        when(userRepo.findByNickname("player")).thenReturn(Optional.of(user));

        ApiResponse<UserResponse> result = userService.login("player", "wrong-password", request, response);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).isEqualTo("昵称或密码错误");
        verify(sessionAuthenticationStrategy, never()).onAuthentication(any(), any(), any());
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    private UserInfo user(Long id, String nickname, String password) {
        UserInfo user = new UserInfo();
        user.setId(id);
        user.setNickname(nickname);
        user.setPassword(password);
        user.setCreatedAt(LocalDateTime.of(2026, 9, 21, 10, 0));
        return user;
    }
}
