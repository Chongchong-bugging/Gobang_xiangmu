package com.gobang.controller;

import com.gobang.dto.ApiResponse;
import com.gobang.dto.GameStateDTO;
import com.gobang.dto.MoveRequest;
import com.gobang.dto.StartGameRequest;
import com.gobang.dto.UnlockBlockRequest;
import com.gobang.entity.UserInfo;
import com.gobang.security.AuthenticatedUser;
import com.gobang.service.GameService;
import com.gobang.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    public ApiResponse<?> start(
            @Valid @RequestBody StartGameRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = checkLogin(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.startGame(user, body.getDifficulty(), body.getGameMode());
        return ApiResponse.success(state);
    }

    /**
     * 玩家落子
     */
    @PostMapping("/move")
    public ApiResponse<?> move(
            @Valid @RequestBody MoveRequest req,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = checkLogin(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.playerMove(user, req.getRow(), req.getCol());
        return ApiResponse.success(state);
    }

    /**
     * AI回合（三人模式下灰子AI落子，由前端在收到 GRAY_TURN 状态后自动调用）
     */
    @PostMapping("/ai-move")
    public ApiResponse<?> aiMove(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = checkLogin(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.aiMove(user);
        return ApiResponse.success(state);
    }

    /**
     * 悔棋
     */
    @PostMapping("/undo")
    public ApiResponse<?> undo(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = checkLogin(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.undo(user);
        return ApiResponse.success(state);
    }

    /**
     * 暂停
     */
    @PostMapping("/pause")
    public ApiResponse<?> pause(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = checkLogin(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.pause(user);
        return ApiResponse.success(state);
    }

    /**
     * 恢复
     */
    @PostMapping("/resume")
    public ApiResponse<?> resume(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = checkLogin(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.resume(user);
        return ApiResponse.success(state);
    }

    /**
     * 解锁区块（动态棋盘模式）
     * @param body 包含 blockRow 和 blockCol 参数
     */
    @PostMapping("/unlock-block")
    public ApiResponse<?> unlockBlock(
            @Valid @RequestBody UnlockBlockRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = checkLogin(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.unlockBlock(user, body.getBlockRow(), body.getBlockCol());
        return ApiResponse.success(state);
    }

    /**
     * 获取当前游戏状态
     */
    @GetMapping("/state")
    public ApiResponse<?> getState(@AuthenticationPrincipal AuthenticatedUser principal) {
        UserInfo user = checkLogin(principal);
        if (user == null) return ApiResponse.error(401, "请先登录");

        GameStateDTO state = gameService.getState(user);
        return ApiResponse.success(state);
    }

    private UserInfo checkLogin(AuthenticatedUser principal) {
        return userService.getUser(principal);
    }
}
