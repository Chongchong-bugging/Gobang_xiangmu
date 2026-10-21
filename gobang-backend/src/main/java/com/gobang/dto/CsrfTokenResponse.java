package com.gobang.dto;

public record CsrfTokenResponse(String headerName, String parameterName, String token) {
}
