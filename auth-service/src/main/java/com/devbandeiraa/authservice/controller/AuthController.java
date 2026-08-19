package com.devbandeiraa.authservice.controller;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.dto.request.LoginRequest;
import com.devbandeiraa.authservice.dto.request.RefreshTokenRequest;
import com.devbandeiraa.authservice.dto.request.RegisterRequest;
import com.devbandeiraa.authservice.dto.response.AuthTokensResponse;
import com.devbandeiraa.authservice.dto.response.UserResponse;
import com.devbandeiraa.authservice.security.AuthenticatedUser;
import com.devbandeiraa.authservice.service.AuthenticationService;
import com.devbandeiraa.authservice.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final AuthenticationService authenticationService;

    public AuthController(
            UserRegistrationService userRegistrationService,
            AuthenticationService authenticationService) {
        this.userRegistrationService = userRegistrationService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registrar(@Valid @RequestBody RegisterRequest requisicao) {
        User usuario = userRegistrationService.registrar(requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.de(usuario));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokensResponse> entrar(@Valid @RequestBody LoginRequest requisicao) {
        return ResponseEntity.ok(authenticationService.autenticar(requisicao));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensResponse> renovar(@Valid @RequestBody RefreshTokenRequest requisicao) {
        return ResponseEntity.ok(authenticationService.renovar(requisicao.refreshToken()));
    }

    /** Idempotente: repetir o logout, ou enviar um token ja invalido, tambem devolve 204. */
    @PostMapping("/logout")
    public ResponseEntity<Void> sair(@Valid @RequestBody RefreshTokenRequest requisicao) {
        authenticationService.encerrarSessao(requisicao.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Devolve a identidade contida no token, sem consultar o banco.
     *
     * <p>Serve tanto ao frontend, para saber quem esta logado, quanto como endpoint de teste da
     * autenticacao: sem token valido aqui, a resposta e 401.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUser> eu(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(usuario);
    }
}
