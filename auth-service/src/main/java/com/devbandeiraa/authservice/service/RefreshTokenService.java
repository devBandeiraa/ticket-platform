package com.devbandeiraa.authservice.service;

import com.devbandeiraa.authservice.domain.RefreshToken;
import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.exception.InvalidRefreshTokenException;
import com.devbandeiraa.authservice.repository.RefreshTokenRepository;
import com.devbandeiraa.authservice.security.TokenLifetimeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Ciclo de vida dos refresh tokens: emissao, rotacao e revogacao. */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    /** 256 bits de entropia: inviavel de adivinhar por forca bruta. */
    private static final int TAMANHO_DO_TOKEN_EM_BYTES = 32;

    private final SecureRandom aleatorio = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenLifetimeProperties propriedades;

    /**
     * Transacao propria para a revogacao em massa disparada por suspeita de reuso.
     *
     * <p>E necessaria porque a revogacao acontece imediatamente antes de lancar a excecao que
     * recusa o token — e essa excecao faria rollback da transacao corrente, desfazendo a propria
     * revogacao. O resultado seria uma resposta 401 sem nenhuma sessao efetivamente derrubada:
     * a protecao existiria so na aparencia.
     */
    private final TransactionTemplate transacaoIndependente;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            TokenLifetimeProperties propriedades,
            PlatformTransactionManager gerenciadorDeTransacao) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.propriedades = propriedades;
        this.transacaoIndependente = new TransactionTemplate(gerenciadorDeTransacao);
        this.transacaoIndependente.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Token entregue ao cliente. O valor em claro so existe aqui e na resposta HTTP. */
    public record TokenEmitido(String valor, Instant expiraEm) {
    }

    @Transactional
    public TokenEmitido emitirPara(User usuario) {
        String valor = gerarValorAleatorio();
        Instant expiraEm = Instant.now().plus(propriedades.refreshTokenTtl());

        refreshTokenRepository.save(new RefreshToken(usuario, calcularHash(valor), expiraEm));

        return new TokenEmitido(valor, expiraEm);
    }

    /**
     * Consome um refresh token, devolvendo o dono para que novos tokens sejam emitidos.
     *
     * <p>O token apresentado e sempre revogado, mesmo em uso legitimo: e a rotacao. Cada refresh
     * troca o token por um novo, entao um token interceptado tem validade de um unico uso.
     *
     * <p>Se chegar um token que ja havia sido revogado, algo esta errado — o legitimo dono ja o
     * trocou, entao quem apresentou este obteve uma copia antiga. Nesse caso todas as sessoes do
     * usuario sao derrubadas, porque nao ha como saber qual das duas partes e a legitima.
     */
    @Transactional
    public User consumir(String valorRecebido) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(calcularHash(valorRecebido))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            UUID usuarioId = token.getUser().getId();
            log.warn("refresh token ja revogado reapresentado (usuario {}); revogando todas as sessoes",
                    usuarioId);

            // Em transacao propria: a excecao lancada logo abaixo desfaria esta revogacao se
            // ela participasse da transacao corrente.
            transacaoIndependente.executeWithoutResult(
                    status -> refreshTokenRepository.revogarTodosDoUsuario(usuarioId));

            throw new InvalidRefreshTokenException();
        }

        if (!token.estaValidoEm(Instant.now())) {
            throw new InvalidRefreshTokenException();
        }

        token.revogar();
        return token.getUser();
    }

    /**
     * Encerra todas as sessoes do dono do token apresentado.
     *
     * <p>Deliberadamente silencioso quando o token nao existe: o logout precisa ser idempotente
     * (clicar duas vezes nao pode dar erro) e uma resposta diferente para token inexistente
     * permitiria testar se um valor qualquer e um token valido.
     */
    @Transactional
    public void encerrarSessoesPeloToken(String valorRecebido) {
        refreshTokenRepository.findByTokenHash(calcularHash(valorRecebido))
                .ifPresent(token -> revogarTodosDe(token.getUser().getId()));
    }

    @Transactional
    public void revogarTodosDe(UUID usuarioId) {
        int revogados = refreshTokenRepository.revogarTodosDoUsuario(usuarioId);
        log.info("logout do usuario {}: {} token(s) revogado(s)", usuarioId, revogados);
    }

    private String gerarValorAleatorio() {
        byte[] bytes = new byte[TAMANHO_DO_TOKEN_EM_BYTES];
        aleatorio.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 basta aqui, e BCrypt seria a escolha errada.
     *
     * <p>O token e um valor aleatorio de 256 bits, nao uma senha escolhida por humano: nao ha
     * dicionario a testar, entao o custo deliberado do BCrypt so tornaria cada refresh mais lento
     * sem ganho de seguranca. O hash existe para que um vazamento do banco nao entregue tokens
     * utilizaveis, e para isso SHA-256 cumpre o papel.
     */
    private String calcularHash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", excecao);
        }
    }
}
