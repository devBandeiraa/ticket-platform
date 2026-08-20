package com.devbandeiraa.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Servico de reserva de ingressos.
 *
 * <p>{@code @ConfigurationPropertiesScan} evita uma classe de configuracao vazia por grupo de
 * propriedades: os records anotados com {@code @ConfigurationProperties} sao encontrados sozinhos
 * dentro deste pacote.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
