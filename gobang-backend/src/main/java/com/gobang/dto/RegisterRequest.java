package com.gobang.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 50, message = "昵称长度应为2到50个字符")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度应为8到64个字符")
    private String password;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
