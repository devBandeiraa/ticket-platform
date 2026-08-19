package com.devbandeiraa.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciais de login.
 *
 * <p>Sem {@code @Email} nem {@code @Size} de proposito: no login, uma validacao de formato
 * responderia 400 antes de tentar autenticar, revelando que aquele valor nem chegou a ser
 * comparado. Credencial errada e credencial errada, e a resposta deve ser sempre a mesma.
 */
public record LoginRequest(

        @NotBlank(message = "O e-mail e obrigatorio")
        String email,

        @NotBlank(message = "A senha e obrigatoria")
        String password) {

    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
