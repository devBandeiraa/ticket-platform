package com.devbandeiraa.authservice.controller;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.dto.request.RegisterRequest;
import com.devbandeiraa.authservice.dto.response.UserResponse;
import com.devbandeiraa.authservice.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacao.
 *
 * <p>O controller so traduz HTTP: recebe o DTO, delega ao servico e devolve o DTO de resposta.
 * Regra de negocio e tratamento de erro ficam fora daqui.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRegistrationService userRegistrationService;

    public AuthController(UserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registrar(@Valid @RequestBody RegisterRequest requisicao) {
        User usuario = userRegistrationService.registrar(requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.de(usuario));
    }
}
