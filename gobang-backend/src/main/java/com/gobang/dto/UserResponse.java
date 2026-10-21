package com.gobang.dto;

import com.gobang.entity.UserInfo;

import java.time.LocalDateTime;

/** Public user representation. Password data must never leave the service layer. */
public record UserResponse(Long id, String nickname, LocalDateTime createdAt) {

    public static UserResponse from(UserInfo user) {
        return new UserResponse(user.getId(), user.getNickname(), user.getCreatedAt());
    }
}
