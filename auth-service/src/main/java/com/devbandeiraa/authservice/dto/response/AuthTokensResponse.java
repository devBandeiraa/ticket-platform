package com.devbandeiraa.authservice.dto.response;

/**
 * Par de tokens devolvido no login e no refresh.
 *
 * @param tokenType sempre {@code Bearer}, para o cliente montar o cabecalho Authorization sem
 *                  precisar saber o esquema de antemao
 * @param expiresIn segundos de validade do access token, para o cliente renovar antes de expirar
 *                  em vez de descobrir pelo primeiro 401
 */
public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {

    public static AuthTokensResponse de(String accessToken, String refreshToken, long expiresIn) {
        return new AuthTokensResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
