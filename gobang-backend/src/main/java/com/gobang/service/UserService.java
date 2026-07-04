package com.gobang.service;

import com.gobang.entity.Score;
import com.gobang.entity.UserInfo;
import com.gobang.repository.ScoreRepository;
import com.gobang.repository.UserInfoRepository;
import com.gobang.dto.ApiResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired private UserInfoRepository userRepo;
    @Autowired private ScoreRepository scoreRepo;

    private static final String SESSION_USER_KEY = "currentUser";

    /**
     * 用户注册
     */
    public ApiResponse<UserInfo> register(String nickname, String password) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return ApiResponse.error(400, "昵称不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            return ApiResponse.error(400, "密码不能为空");
        }
        if (nickname.length() > 50) {
            return ApiResponse.error(400, "昵称过长（最多50字符）");
        }

        if (userRepo.existsByNickname(nickname)) {
            return ApiResponse.error(400, "昵称已存在");
        }

        UserInfo user = new UserInfo();
        user.setNickname(nickname);
        user.setPassword(password);
        user = userRepo.save(user);

        // 初始化得分记录
        Score score = new Score();
        score.setUserId(user.getId());
        scoreRepo.save(score);

        return ApiResponse.success("注册成功", user);
    }

    /**
     * 用户登录
     */
    public ApiResponse<UserInfo> login(String nickname, String password, HttpSession session) {
        if (nickname == null || password == null) {
            return ApiResponse.error(400, "昵称和密码不能为空");
        }

        Optional<UserInfo> opt = userRepo.findByNickname(nickname);
        if (opt.isEmpty()) {
            return ApiResponse.error(400, "用户不存在");
        }

        UserInfo user = opt.get();
        if (!user.getPassword().equals(password)) {
            return ApiResponse.error(400, "密码错误");
        }

        // 设置Session
        session.setAttribute(SESSION_USER_KEY, user);
        session.setMaxInactiveInterval(30 * 60); // 30分钟

        return ApiResponse.success("登录成功", user);
    }

    /**
     * 注销
     */
    public ApiResponse<Void> logout(HttpSession session) {
        session.removeAttribute(SESSION_USER_KEY);
        session.invalidate();
        return ApiResponse.success("已注销", null);
    }

    /**
     * 获取当前登录用户
     */
    public UserInfo getCurrentUser(HttpSession session) {
        return (UserInfo) session.getAttribute(SESSION_USER_KEY);
    }

    /**
     * 判断是否已登录
     */
    public boolean isLoggedIn(HttpSession session) {
        return getCurrentUser(session) != null;
    }
}
