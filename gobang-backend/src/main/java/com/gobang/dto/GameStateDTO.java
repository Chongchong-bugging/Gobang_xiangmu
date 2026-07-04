package com.gobang.dto;

public class GameStateDTO {
    private Long gameId;
    private int[][] board;
    private int[][] terrain;     // 地形数据: 0=无, 4=假山, 5=水潭 (障碍物模式)
    private String status;      // WAITING, PLAYER_TURN, AI_THINKING, PLAYER_WIN, AI_WIN, WHITE_WIN, GRAY_WIN, DRAW, PAUSED
    private String difficulty;  // EASY, MEDIUM, HARD
    private int undoRemaining;
    private boolean paused;
    private String message;
    private String gameMode;     // "TWO", "THREE" or "OBSTACLE"
    private Integer currentTurn; // BLACK, WHITE, or GRAY (三人模式用)

    public Long getGameId() { return gameId; }
    public void setGameId(Long gameId) { this.gameId = gameId; }

    public int[][] getBoard() { return board; }
    public void setBoard(int[][] board) { this.board = board; }

    public int[][] getTerrain() { return terrain; }
    public void setTerrain(int[][] terrain) { this.terrain = terrain; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getUndoRemaining() { return undoRemaining; }
    public void setUndoRemaining(int undoRemaining) { this.undoRemaining = undoRemaining; }

    public boolean isPaused() { return paused; }
    public void setPaused(boolean paused) { this.paused = paused; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getGameMode() { return gameMode; }
    public void setGameMode(String gameMode) { this.gameMode = gameMode; }

    public Integer getCurrentTurn() { return currentTurn; }
    public void setCurrentTurn(Integer currentTurn) { this.currentTurn = currentTurn; }
}
