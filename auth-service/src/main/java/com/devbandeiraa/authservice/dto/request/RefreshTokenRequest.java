package com.devbandeiraa.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Corpo de {@code POST /auth/refresh} e de {@code POST /auth/logout}. */
public record RefreshTokenRequest(

        @NotBlank(message = "O refresh token e obrigatorio")
        String refreshToken) {
}
