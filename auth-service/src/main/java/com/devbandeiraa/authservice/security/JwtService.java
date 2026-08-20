package com.devbandeiraa.authservice.security;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.shared.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emite os access tokens.
 *
 * <p>Contraparte do {@code JwtTokenReader} do modulo compartilhado: aqui se cria o token, la se
 * confere. A separacao e proposital — so o auth-service consegue emitir, enquanto qualquer
 * servico consegue validar.
 *
 * <p>Assinatura em HS256 com segredo compartilhado, o que permite ao gateway validar sozinho, sem
 * chamar este servico a cada requisicao. A contrapartida conhecida e que quem valida tambem
 * consegue emitir — limitacao aceita no escopo e registrada no risco #14 do mapeamento.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    /** Fixo, e nao inferido do tamanho da chave. Ver comentario na emissao do token. */
    private static final MacAlgorithm ALGORITMO = Jwts.SIG.HS256;

    private final SecretKey chave;
    private final String emissor;
    private final TokenLifetimeProperties validade;

    public JwtService(JwtProperties propriedades, TokenLifetimeProperties validade) {
        this.chave = Keys.hmacShaKeyFor(propriedades.secret().getBytes(StandardCharsets.UTF_8));
        this.emissor = propriedades.issuer();
        this.validade = validade;
    }

    /**
     * Gera o access token de um usuario.
     *
     * <p>O papel viaja dentro do token para que os demais servicos autorizem por role sem
     * consultar o auth-service. Em troca, uma promocao a ADMIN so passa a valer no proximo
     * token emitido.
     */
    public String gerarAccessToken(User usuario) {
        Instant agora = Instant.now();

        return Jwts.builder()
                .issuer(emissor)
                .subject(usuario.getId().toString())
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_ROLE, usuario.getRole().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(validade.accessTokenTtl())))
                // Algoritmo fixado de proposito. Sem o segundo argumento, o jjwt infere o
                // algoritmo mais forte que o tamanho da chave suporta — trocar o segredo por um
                // mais longo ou mais curto mudaria HS256 para HS384 ou HS512 sem aviso, e o
                // gateway, configurado para outro algoritmo, passaria a recusar todo token.
                .signWith(chave, ALGORITMO)
                .compact();
    }

    /** Segundos de vida do access token, informado ao cliente na resposta do login. */
    public long segundosDeValidadeDoAccessToken() {
        return validade.accessTokenTtl().toSeconds();
    }
}
