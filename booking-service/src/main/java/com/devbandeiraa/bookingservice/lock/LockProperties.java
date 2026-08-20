package com.devbandeiraa.bookingservice.lock;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes do lock distribuido.
 *
 * <p>O TTL merece atencao: e o tempo maximo que um lock sobrevive sem ser liberado. Curto
 * demais, expira no meio da secao critica e deixa dois processos se acharem donos; longo demais,
 * um processo que morre logo apos adquirir o lock trava as reservas daquele evento pelo periodo
 * inteiro. Tres segundos e uma ordem de grandeza acima da transacao que ele protege — um
 * {@code UPDATE} e um {@code INSERT} — e ainda assim curto o bastante para nao ser sentido.
 *
 * @param ttl               validade do lock no Redis
 * @param tentativas        quantas vezes tentar adquirir antes de desistir
 * @param esperaEntreTentativas pausa entre as tentativas
 */
@ConfigurationProperties(prefix = "booking.lock")
public record LockProperties(Duration ttl, int tentativas, Duration esperaEntreTentativas) {

    public LockProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("booking.lock.ttl precisa ser positivo");
        }
        if (tentativas < 1) {
            throw new IllegalArgumentException("booking.lock.tentativas precisa ser no minimo 1");
        }
        if (esperaEntreTentativas == null || esperaEntreTentativas.isNegative()) {
            throw new IllegalArgumentException(
                    "booking.lock.espera-entre-tentativas nao pode ser negativa");
        }
    }
}
