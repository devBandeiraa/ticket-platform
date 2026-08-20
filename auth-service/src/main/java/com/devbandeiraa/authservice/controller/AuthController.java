package com.devbandeiraa.authservice.controller;

import com.devbandeiraa.authservice.config.OpenApiConfig;
import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.dto.request.LoginRequest;
import com.devbandeiraa.authservice.dto.request.RefreshTokenRequest;
import com.devbandeiraa.authservice.dto.request.RegisterRequest;
import com.devbandeiraa.authservice.dto.response.AuthTokensResponse;
import com.devbandeiraa.authservice.dto.response.UserResponse;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import com.devbandeiraa.authservice.service.AuthenticationService;
import com.devbandeiraa.authservice.service.UserRegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Autenticacao",
        description = "Cadastro, login, renovacao e encerramento de sessao.")
public class AuthController {

    private final UserRegistrationService userRegistrationService;
    private final AuthenticationService authenticationService;

    public AuthController(
            UserRegistrationService userRegistrationService,
            AuthenticationService authenticationService) {
        this.userRegistrationService = userRegistrationService;
        this.authenticationService = authenticationService;
    }

    @Operation(summary = "Cria uma conta",
            description = "O papel e sempre USER. Criar administradores nao passa por aqui.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "conta criada"),
            @ApiResponse(responseCode = "400", description = "VALIDATION_ERROR: dados invalidos",
                    content = @Content),
            @ApiResponse(responseCode = "409",
                    description = "EMAIL_ALREADY_REGISTERED: o e-mail ja tem conta",
                    content = @Content)})
    @PostMapping("/register")
    public ResponseEntity<UserResponse> registrar(@Valid @RequestBody RegisterRequest requisicao) {
        User usuario = userRegistrationService.registrar(requisicao);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.de(usuario));
    }

    @Operation(summary = "Autentica e devolve o par de tokens",
            description = "O `accessToken` tem vida curta e vai no cabecalho `Authorization`. "
                    + "O `refreshToken` tem vida longa e serve apenas para obter um novo par.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "autenticado"),
            @ApiResponse(responseCode = "401",
                    description = "INVALID_CREDENTIALS: e-mail ou senha incorretos",
                    content = @Content),
            @ApiResponse(responseCode = "429",
                    description = "RATE_LIMIT_EXCEEDED: o gateway aplica um balde estreito "
                            + "sobre este caminho, de cerca de cinco tentativas por minuto",
                    content = @Content)})
    @PostMapping("/login")
    public ResponseEntity<AuthTokensResponse> entrar(@Valid @RequestBody LoginRequest requisicao) {
        return ResponseEntity.ok(authenticationService.autenticar(requisicao));
    }

    @Operation(summary = "Troca o refresh token por um par novo",
            description = "O refresh token usado e invalidado na troca: reapresenta-lo devolve "
                    + "401, o que limita o estrago de um token vazado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "par renovado"),
            @ApiResponse(responseCode = "401",
                    description = "SESSION_EXPIRED: token ausente, vencido ou ja usado",
                    content = @Content)})
    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensResponse> renovar(@Valid @RequestBody RefreshTokenRequest requisicao) {
        return ResponseEntity.ok(authenticationService.renovar(requisicao.refreshToken()));
    }

    /** Idempotente: repetir o logout, ou enviar um token ja invalido, tambem devolve 204. */
    @Operation(summary = "Encerra a sessao",
            description = "Idempotente: repetir a chamada, ou enviar um token ja invalido, "
                    + "tambem devolve 204. Sair duas vezes nao e erro.")
    @ApiResponse(responseCode = "204", description = "sessao encerrada")
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
    @Operation(summary = "Devolve a identidade do token",
            description = "Le o token e devolve quem ele identifica, sem consultar o banco. "
                    + "Serve tambem para testar se a autenticacao esta funcionando.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "identidade do portador do token"),
            @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: ausente ou invalido",
                    content = @Content)})
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_JWT)
    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUser> eu(@AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(usuario);
    }
}
