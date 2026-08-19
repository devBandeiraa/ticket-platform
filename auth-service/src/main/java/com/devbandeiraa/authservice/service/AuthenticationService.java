package com.devbandeiraa.authservice.service;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.dto.request.LoginRequest;
import com.devbandeiraa.authservice.dto.response.AuthTokensResponse;
import com.devbandeiraa.authservice.exception.InvalidCredentialsException;
import com.devbandeiraa.authservice.repository.UserRepository;
import com.devbandeiraa.authservice.security.JwtService;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Autenticacao, renovacao de tokens e encerramento de sessao. */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    /**
     * Hash BCrypt valido usado apenas para gastar tempo quando o e-mail nao existe.
     *
     * <p>Sem isso, um e-mail inexistente responderia bem mais rapido que um e-mail real com senha
     * errada — porque o BCrypt nem chegaria a rodar. Essa diferenca de tempo, medida em escala,
     * permite descobrir quais e-mails estao cadastrados.
     */
    private static final String HASH_DE_COMPARACAO_FICTICIA =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthenticationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Autentica e emite o par de tokens.
     *
     * @throws InvalidCredentialsException se o e-mail nao existir, a senha nao conferir ou a
     *                                     conta estiver desabilitada — sempre com a mesma
     *                                     mensagem, para nao revelar qual dos casos ocorreu
     */
    @Transactional
    public AuthTokensResponse autenticar(LoginRequest requisicao) {
        String email = normalizarEmail(requisicao.email());
        Optional<User> encontrado = userRepository.findByEmail(email);

        if (encontrado.isEmpty()) {
            passwordEncoder.matches(requisicao.password(), HASH_DE_COMPARACAO_FICTICIA);
            log.info("tentativa de login com e-mail inexistente");
            throw new InvalidCredentialsException();
        }

        User usuario = encontrado.get();

        if (!passwordEncoder.matches(requisicao.password(), usuario.getPasswordHash())) {
            log.info("senha incorreta para o usuario {}", usuario.getId());
            throw new InvalidCredentialsException();
        }

        if (!usuario.isEnabled()) {
            log.info("login recusado: usuario {} desabilitado", usuario.getId());
            throw new InvalidCredentialsException();
        }

        log.info("login efetuado: usuario {}", usuario.getId());
        return emitirTokens(usuario);
    }

    /**
     * Troca um refresh token valido por um novo par.
     *
     * <p>O token apresentado e invalidado no processo, entao cada refresh so pode ser usado uma
     * vez. A regra de rotacao vive no {@link RefreshTokenService}.
     */
    @Transactional
    public AuthTokensResponse renovar(String refreshToken) {
        User usuario = refreshTokenService.consumir(refreshToken);
        log.info("tokens renovados para o usuario {}", usuario.getId());
        return emitirTokens(usuario);
    }

    /**
     * Encerra a sessao revogando os refresh tokens do usuario.
     *
     * <p>O access token ja emitido continua valido ate expirar — nao ha como invalidar um JWT
     * sem manter uma lista negra, e por isso o tempo de vida dele e curto. O efeito pratico e
     * que o usuario perde o acesso no maximo alguns minutos depois do logout.
     */
    @Transactional
    public void encerrarSessao(String refreshToken) {
        refreshTokenService.encerrarSessoesPeloToken(refreshToken);
    }

    private AuthTokensResponse emitirTokens(User usuario) {
        String accessToken = jwtService.gerarAccessToken(usuario);
        RefreshTokenService.TokenEmitido refreshToken = refreshTokenService.emitirPara(usuario);

        return AuthTokensResponse.de(
                accessToken,
                refreshToken.valor(),
                jwtService.segundosDeValidadeDoAccessToken());
    }

    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
