package com.devbandeiraa.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Servico de notificacao.
 *
 * <p>Nao expoe API de negocio e nao tem banco. Consome eventos de reserva do RabbitMQ e registra
 * a notificacao. O unico endpoint HTTP e o {@code /actuator/health}, que existe para o
 * docker-compose e o Kubernetes saberem se o servico esta de pe.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
