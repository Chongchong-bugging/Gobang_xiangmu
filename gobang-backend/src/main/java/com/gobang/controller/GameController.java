package com.gobang.controller;

import com.gobang.dto.ApiResponse;
import com.gobang.dto.GameStateDTO;
import com.gobang.dto.MoveRequest;
import com.gobang.entity.UserInfo;
import com.gobang.service.GameService;
import com.gobang.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {

    @Autowired private GameService gameService;
    @Autowired private UserService userService;

    /**
     * 开始新游戏
     * @param body 包含 difficulty 和 gameMode 参数
     */
    @PostMapping("/start")
    public ApiResponse<?> start(@RequestBody Map<String, String> body, HttpSession session) {
        UserInfo user = checkLogin(session);
        if (user == null) return ApiResponse.error(401, "请先登录");

        String difficulty = body.getOrDefault("difficulty", "EASY");
        String gameMode = body.getOrDefault("gameMode", "TWO");
        GameStateDTO state = gameService.startGame(user, difficulty, gameMode);
        return ApiResponse.success(state);
    }

    /**
     * 玩家落子
     */
    @PostMapping("/move")
    public ApiResponse<?> move(@RequestBody MoveRequest req, HttpSession session) {
        UserInfo user = checkLogin(session);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.playerMove(user, req.getRow(), req.getCol());
        return ApiResponse.success(state);
    }

    /**
     * AI回合（三人模式下灰子AI落子，由前端在收到 GRAY_TURN 状态后自动调用）
     */
    @PostMapping("/ai-move")
    public ApiResponse<?> aiMove(HttpSession session) {
        UserInfo user = checkLogin(session);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.aiMove(user);
        return ApiResponse.success(state);
    }

    /**
     * 悔棋
     */
    @PostMapping("/undo")
    public ApiResponse<?> undo(HttpSession session) {
        UserInfo user = checkLogin(session);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.undo(user);
        return ApiResponse.success(state);
    }

    /**
     * 暂停
     */
    @PostMapping("/pause")
    public ApiResponse<?> pause(HttpSession session) {
        UserInfo user = checkLogin(session);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.pause(user);
        return ApiResponse.success(state);
    }

    /**
     * 恢复
     */
    @PostMapping("/resume")
    public ApiResponse<?> resume(HttpSession session) {
        UserInfo user = checkLogin(session);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.resume(user);
        return ApiResponse.success(state);
    }

    /**
     * 获取当前游戏状态
     */
    @GetMapping("/state")
    public ApiResponse<?> getState(HttpSession session) {
        UserInfo user = checkLogin(session);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.getState(user);
        return ApiResponse.success(state);
    }

    private UserInfo checkLogin(HttpSession session) {
        return userService.getCurrentUser(session);
    }
}
