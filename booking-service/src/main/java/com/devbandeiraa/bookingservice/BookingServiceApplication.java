package com.devbandeiraa.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Servico de reserva de ingressos.
 *
 * <p>{@code @ConfigurationPropertiesScan} evita uma classe de configuracao vazia por grupo de
 * propriedades: os records anotados com {@code @ConfigurationProperties} sao encontrados sozinhos
 * dentro deste pacote.
 *
 * <p>{@code @EnableScheduling} liga a varredura que expira reservas vencidas e devolve o estoque
 * que elas retinham.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
