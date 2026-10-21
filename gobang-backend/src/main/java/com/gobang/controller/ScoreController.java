package com.gobang.controller;

import com.gobang.dto.ApiResponse;
import com.gobang.dto.ScoreDTO;
import com.gobang.entity.UserInfo;
import com.gobang.security.AuthenticatedUser;
import com.gobang.service.ScoreService;
import com.gobang.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/score")
public class ScoreController {

    @Autowired private ScoreService scoreService;
    @Autowired private UserService userService;

    @GetMapping("/my")
    public ApiResponse<?> getMyScore(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = userService.getUser(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        ScoreDTO dto = scoreService.getMyScore(user.getId());
        if (dto == null) return ApiResponse.error(400, "用户不存在");
        return ApiResponse.success(dto);
    }
}
