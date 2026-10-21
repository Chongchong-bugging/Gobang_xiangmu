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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserInfoRepository userRepo;
    private final ScoreRepository scoreRepo;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public UserService(
            UserInfoRepository userRepo,
            ScoreRepository scoreRepo,
            PasswordEncoder passwordEncoder,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.userRepo = userRepo;
        this.scoreRepo = scoreRepo;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode("invalid-account-placeholder");
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    /** Register a user and its score account in one database transaction. */
    @Transactional
    public ApiResponse<UserResponse> register(String nickname, String password) {
        String normalizedNickname = nickname.trim();
        if (userRepo.existsByNickname(normalizedNickname)) {
            return ApiResponse.error(400, "昵称已存在");
        }

        UserInfo user = new UserInfo();
        user.setNickname(normalizedNickname);
        user.setPassword(passwordEncoder.encode(password));
        user = userRepo.save(user);

        Score score = new Score();
        score.setUserId(user.getId());
        scoreRepo.save(score);

        return ApiResponse.success("注册成功", UserResponse.from(user));
    }

    /**
     * Authenticate a JSON login and explicitly persist the Spring Security context.
     * Legacy plaintext passwords are upgraded to BCrypt after one successful login.
     */
    @Transactional
    public ApiResponse<UserResponse> login(
            String nickname,
            String password,
            HttpServletRequest request,
            HttpServletResponse response) {
        String normalizedNickname = nickname.trim();
        Optional<UserInfo> candidate = userRepo.findByNickname(normalizedNickname);
        if (candidate.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash);
            return ApiResponse.error(400, "昵称或密码错误");
        }
        if (!passwordMatches(password, candidate.get().getPassword())) {
            return ApiResponse.error(400, "昵称或密码错误");
        }

        UserInfo user = candidate.get();
        if (!isBcryptHash(user.getPassword()) || passwordEncoder.upgradeEncoding(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(password));
            user = userRepo.save(user);
        }

        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getNickname());
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ApiResponse.success("登录成功", UserResponse.from(user));
    }

    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication());
        return ApiResponse.success("已注销", null);
    }

    @Transactional(readOnly = true)
    public UserInfo getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return null;
        }
        return userRepo.findById(principal.id()).orElse(null);
    }

    @Transactional(readOnly = true)
    public UserInfo getUser(AuthenticatedUser principal) {
        if (principal == null) return null;
        return userRepo.findById(principal.id()).orElse(null);
    }

    public boolean isLoggedIn() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser;
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (storedPassword == null) return false;
        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }

        // Temporary compatibility path for accounts created before BCrypt was introduced.
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedPassword.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBcryptHash(String value) {
        return value != null
                && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }
}
