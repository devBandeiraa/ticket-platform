package com.devbandeiraa.authservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.repository.UserRepository;
import com.devbandeiraa.authservice.support.PostgresContainerConfig;
import com.devbandeiraa.shared.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica o seed do administrador de desenvolvimento.
 *
 * <p>O valor deste teste esta em travar a correspondencia entre o hash gravado na migration e a
 * senha documentada no cabecalho dela. Sao dois valores que precisam concordar e que nada mais
 * obriga a concordar: alguem trocando o hash sem atualizar o comentario, ou vice-versa, deixaria
 * a documentacao mentindo — e o proximo a tentar entrar simplesmente nao conseguiria, sem pista
 * do motivo.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
@ActiveProfiles("dev")
class SeedAdminDesenvolvimentoIntegrationTest {

    /** Exatamente as credenciais documentadas no cabecalho da migration. */
    private static final String EMAIL_DO_ADMIN = "admin@ticket.dev";
    private static final String SENHA_DO_ADMIN = "admin@ticket.dev123";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("com o profile dev, o administrador nasce habilitado e com papel ADMIN")
    void deveCriarAdministradorNoProfileDev() {
        User admin = userRepository.findByEmail(EMAIL_DO_ADMIN).orElseThrow(
                () -> new AssertionError("o seed do profile dev nao criou o administrador"));

        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("a senha documentada na migration realmente abre a conta")
    void aSenhaDocumentadaDeveFuncionar() {
        User admin = userRepository.findByEmail(EMAIL_DO_ADMIN).orElseThrow();

        assertThat(passwordEncoder.matches(SENHA_DO_ADMIN, admin.getPasswordHash()))
                .as("o hash da migration precisa corresponder a senha documentada no cabecalho dela")
                .isTrue();
    }
}
