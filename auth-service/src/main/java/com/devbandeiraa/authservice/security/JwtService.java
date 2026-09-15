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

    /**
     * Nome do usuario.
     *
     * <p>{@code name} e o nome padronizado pelo OpenID Connect para este dado. Usar o nome
     * consagrado, e nao um {@code fullName} inventado, deixa o token legivel por qualquer
     * ferramenta que ja saiba ler JWT.
     */
    private static final String CLAIM_NAME = "name";

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
     *
     * <p>O nome viaja pela mesma razao, e paga o mesmo preco. O ingresso digital precisa
     * imprimir quem comprou, e sem o claim {@code /auth/me} teria de ir ao banco a cada
     * requisicao autenticada — desfazendo justamente a propriedade que motivou o JWT aqui.
     * Em troca, quem mudar o proprio nome so o ve atualizado no proximo token.
     *
     * <p>Nao ha risco de vazamento novo: o token e portado pelo dono, e o nome ja e dele.
     */
    public String gerarAccessToken(User usuario) {
        Instant agora = Instant.now();

        return Jwts.builder()
                .issuer(emissor)
                .subject(usuario.getId().toString())
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_ROLE, usuario.getRole().name())
                .claim(CLAIM_NAME, usuario.getFullName())
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
