package com.devbandeiraa.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// Registra os records anotados com @ConfigurationProperties deste modulo — hoje, o do painel de
// status. Sem isto eles nao viram bean e a aplicacao nao sobe.
@ConfigurationPropertiesScan
@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
