package com.gobang.controller;

import com.gobang.dto.ApiResponse;
import com.gobang.dto.LoginRequest;
import com.gobang.dto.RegisterRequest;
import com.gobang.entity.UserInfo;
import com.gobang.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired private UserService userService;

    @PostMapping("/register")
    public ApiResponse<?> register(@RequestBody RegisterRequest req) {
        return userService.register(req.getNickname(), req.getPassword());
    }

    @PostMapping("/login")
    public ApiResponse<?> login(@RequestBody LoginRequest req, HttpSession session) {
        // 检查是否重复登录
        if (userService.isLoggedIn(session)) {
            return ApiResponse.error(400, "已登录，请先注销");
        }
        return userService.login(req.getNickname(), req.getPassword(), session);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        return userService.logout(session);
    }

    @GetMapping("/info")
    public ApiResponse<?> getInfo(HttpSession session) {
        UserInfo user = userService.getCurrentUser(session);
        if (user == null) return ApiResponse.error(401, "未登录");
        return ApiResponse.success(user);
    }
}
