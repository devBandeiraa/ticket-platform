package com.devbandeiraa.authservice.security;

import com.devbandeiraa.authservice.domain.Role;
import com.devbandeiraa.authservice.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emite e valida os access tokens.
 *
 * <p>Assinatura em HS256 com segredo compartilhado: o api-gateway consegue validar o token
 * sozinho, sem chamar este servico a cada requisicao. A contrapartida conhecida e que quem valida
 * tambem consegue emitir — limitacao aceita no escopo e registrada no risco #14 do mapeamento.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    /** Fixo, e nao inferido do tamanho da chave. Ver comentario na emissao do token. */
    private static final MacAlgorithm ALGORITMO = Jwts.SIG.HS256;

    private final SecretKey chave;
    private final JwtProperties propriedades;

    public JwtService(JwtProperties propriedades) {
        this.propriedades = propriedades;
        this.chave = Keys.hmacShaKeyFor(propriedades.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gera o access token de um usuario.
     *
     * <p>O papel viaja dentro do token para que o gateway autorize por role sem consultar o
     * auth-service. Em troca, uma promocao a ADMIN so passa a valer no proximo token emitido.
     */
    public String gerarAccessToken(User usuario) {
        Instant agora = Instant.now();

        return Jwts.builder()
                .issuer(propriedades.issuer())
                .subject(usuario.getId().toString())
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_ROLE, usuario.getRole().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(propriedades.accessTokenTtl())))
                // Algoritmo fixado de proposito. Sem o segundo argumento, o jjwt infere o
                // algoritmo mais forte que o tamanho da chave suporta — trocar o segredo por um
                // mais longo ou mais curto mudaria HS256 para HS384 ou HS512 sem aviso, e o
                // gateway, configurado para outro algoritmo, passaria a recusar todo token.
                .signWith(chave, ALGORITMO)
                .compact();
    }

    /**
     * Valida a assinatura e o prazo do token e devolve quem ele representa.
     *
     * @throws JwtException se o token estiver expirado, adulterado, assinado com outra chave ou
     *                      emitido por outro issuer
     */
    public AuthenticatedUser extrairUsuario(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(propriedades.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)));
    }

    /** Segundos de vida do access token, informado ao cliente na resposta do login. */
    public long segundosDeValidadeDoAccessToken() {
        return propriedades.accessTokenTtl().toSeconds();
    }
}
