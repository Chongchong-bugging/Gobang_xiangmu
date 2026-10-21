package com.gobang.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_result")
public class GameResult {

    @Id
    @Column(name = "game_id", length = 36, nullable = false, updatable = false)
    private String gameId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 8, updatable = false)
    private String result;

    @Column(name = "score_delta", nullable = false, updatable = false)
    private Integer scoreDelta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public Integer getScoreDelta() { return scoreDelta; }
    public void setScoreDelta(Integer scoreDelta) { this.scoreDelta = scoreDelta; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
