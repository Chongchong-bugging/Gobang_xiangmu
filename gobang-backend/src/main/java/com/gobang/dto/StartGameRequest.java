package com.gobang.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class StartGameRequest {

    @NotBlank(message = "游戏难度不能为空")
    @Pattern(regexp = "EASY|MEDIUM|HARD", message = "不支持的游戏难度")
    private String difficulty = "EASY";

    @NotBlank(message = "游戏模式不能为空")
    @Pattern(
            regexp = "TWO|THREE|OBSTACLE|THREE_OBSTACLE|DYNAMIC",
            message = "不支持的游戏模式")
    private String gameMode = "TWO";

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }
}
