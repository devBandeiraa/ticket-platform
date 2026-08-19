package com.devbandeiraa.authservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.dto.request.LoginRequest;
import com.devbandeiraa.authservice.dto.response.AuthTokensResponse;
import com.devbandeiraa.authservice.exception.InvalidCredentialsException;
import com.devbandeiraa.authservice.repository.UserRepository;
import com.devbandeiraa.authservice.security.JwtService;
import com.devbandeiraa.authservice.service.AuthenticationService;
import com.devbandeiraa.authservice.service.RefreshTokenService;
import com.devbandeiraa.authservice.support.UsuarioDeTeste;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Testes de unidade da autenticacao. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationServiceTest {

    private static final String SENHA = "senhaSegura123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthenticationService service;

    @Test
    @DisplayName("emite access token e refresh token quando as credenciais conferem")
    void deveEmitirTokensNoLogin() {
        User usuario = UsuarioDeTeste.comum("joao@email.com");
        when(userRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(eq(SENHA), any())).thenReturn(true);
        when(jwtService.gerarAccessToken(usuario)).thenReturn("access-token-falso");
        when(jwtService.segundosDeValidadeDoAccessToken()).thenReturn(900L);
        when(refreshTokenService.emitirPara(usuario))
                .thenReturn(new RefreshTokenService.TokenEmitido("refresh-token-falso", Instant.now()));

        AuthTokensResponse resposta = service.autenticar(new LoginRequest("joao@email.com", SENHA));

        assertThat(resposta.accessToken()).isEqualTo("access-token-falso");
        assertThat(resposta.refreshToken()).isEqualTo("refresh-token-falso");
        assertThat(resposta.tokenType()).isEqualTo("Bearer");
        assertThat(resposta.expiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("normaliza o e-mail antes de buscar, tornando o login insensivel a caixa")
    void deveNormalizarEmailNoLogin() {
        User usuario = UsuarioDeTeste.comum("joao@email.com");
        when(userRepository.findByEmail("joao@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(eq(SENHA), any())).thenReturn(true);
        when(refreshTokenService.emitirPara(usuario))
                .thenReturn(new RefreshTokenService.TokenEmitido("refresh", Instant.now()));

        service.autenticar(new LoginRequest("  JOAO@Email.COM  ", SENHA));

        verify(userRepository).findByEmail("joao@email.com");
    }

    @Test
    @DisplayName("recusa senha incorreta sem emitir token algum")
    void deveRecusarSenhaIncorreta() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(UsuarioDeTeste.comum("joao@email.com")));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.autenticar(new LoginRequest("joao@email.com", "errada")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenService, never()).emitirPara(any());
    }

    @Test
    @DisplayName("recusa conta desabilitada mesmo com a senha correta")
    void deveRecusarContaDesabilitada() {
        when(userRepository.findByEmail(anyString()))
                .thenReturn(Optional.of(UsuarioDeTeste.desabilitado("joao@email.com")));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.autenticar(new LoginRequest("joao@email.com", SENHA)))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(refreshTokenService, never()).emitirPara(any());
    }

    @Test
    @DisplayName("compara contra um hash ficticio quando o e-mail nao existe, equalizando o tempo")
    void deveGastarTempoQuandoEmailNaoExiste() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.autenticar(new LoginRequest("ninguem@email.com", SENHA)))
                .isInstanceOf(InvalidCredentialsException.class);

        // Sem esta comparacao, um e-mail inexistente responderia visivelmente mais rapido que um
        // e-mail real com senha errada, e a diferenca revelaria quais contas existem.
        verify(passwordEncoder, atLeastOnce()).matches(eq(SENHA), anyString());
    }

    @Test
    @DisplayName("a mensagem de erro e identica nos tres casos, sem revelar qual ocorreu")
    void deveUsarMensagemGenericaEmTodasAsFalhas() {
        when(userRepository.findByEmail("inexistente@email.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existente@email.com"))
                .thenReturn(Optional.of(UsuarioDeTeste.comum("existente@email.com")));
        when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

        String mensagemEmailInexistente = capturarMensagem("inexistente@email.com");
        String mensagemSenhaErrada = capturarMensagem("existente@email.com");

        assertThat(mensagemEmailInexistente).isEqualTo(mensagemSenhaErrada);
    }

    @Test
    @DisplayName("renova os tokens consumindo o refresh token apresentado")
    void deveRenovarTokens() {
        User usuario = UsuarioDeTeste.comum("joao@email.com");
        when(refreshTokenService.consumir("refresh-antigo")).thenReturn(usuario);
        when(jwtService.gerarAccessToken(usuario)).thenReturn("novo-access");
        when(refreshTokenService.emitirPara(usuario))
                .thenReturn(new RefreshTokenService.TokenEmitido("novo-refresh", Instant.now()));

        AuthTokensResponse resposta = service.renovar("refresh-antigo");

        assertThat(resposta.accessToken()).isEqualTo("novo-access");
        assertThat(resposta.refreshToken()).isEqualTo("novo-refresh");
        // O token antigo tem que ser consumido, senao a rotacao nao acontece.
        verify(refreshTokenService).consumir("refresh-antigo");
    }

    private String capturarMensagem(String email) {
        try {
            service.autenticar(new LoginRequest(email, SENHA));
            throw new AssertionError("esperava InvalidCredentialsException");
        } catch (InvalidCredentialsException excecao) {
            return excecao.getMessage();
        }
    }
}
