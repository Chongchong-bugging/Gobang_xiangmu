package com.gobang.controller;

import com.gobang.dto.ApiResponse;
import com.gobang.dto.CsrfTokenResponse;
import com.gobang.dto.LoginRequest;
import com.gobang.dto.RegisterRequest;
import com.gobang.dto.UserResponse;
import com.gobang.entity.UserInfo;
import com.gobang.security.AuthenticatedUser;
import com.gobang.security.RequestRateLimiter;
import com.gobang.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired private UserService userService;
    @Autowired private RequestRateLimiter rateLimiter;

    @PostMapping("/register")
    public ApiResponse<?> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest request) {
        rateLimiter.enforce("register:" + request.getRemoteAddr(), 5, Duration.ofHours(1));
        return userService.register(req.getNickname(), req.getPassword());
    }

    @PostMapping("/login")
    public ApiResponse<?> login(
            @Valid @RequestBody LoginRequest req,
            HttpServletRequest request,
            HttpServletResponse response) {
        // 检查是否重复登录
        if (userService.isLoggedIn()) {
            return ApiResponse.error(400, "已登录，请先注销");
        }
        rateLimiter.enforce("login-ip:" + request.getRemoteAddr(), 30, Duration.ofMinutes(15));
        rateLimiter.enforce("login-name:" + request.getRemoteAddr() + ":"
                + req.getNickname().trim().toLowerCase(java.util.Locale.ROOT), 10, Duration.ofMinutes(15));
        return userService.login(req.getNickname(), req.getPassword(), request, response);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        return userService.logout(request, response);
    }

    @GetMapping("/info")
    public ApiResponse<?> getInfo(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = userService.getUser(principal);
        if (user == null) return ApiResponse.error(401, "未登录");
        return ApiResponse.success(UserResponse.from(user));
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfTokenResponse> csrf(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return ApiResponse.success(new CsrfTokenResponse(
                csrfToken.getHeaderName(),
                csrfToken.getParameterName(),
                csrfToken.getToken()));
    }
}
