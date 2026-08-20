package com.devbandeiraa.authservice.support;

import com.devbandeiraa.shared.security.Role;
import com.devbandeiraa.authservice.domain.User;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Constroi usuarios ja "persistidos" para testes de unidade.
 *
 * <p>O id de um {@link User} e gerado pelo Hibernate na gravacao, entao uma instancia recem
 * criada tem id nulo. Como varios testes precisam de um usuario com identidade sem envolver
 * banco, o id e injetado por reflexao — restrito ao codigo de teste, para nao abrir um setter
 * publico que so existiria por causa deles.
 */
public final class UsuarioDeTeste {

    private UsuarioDeTeste() {
    }

    public static User comum(String email) {
        return comIdEPapel(UUID.randomUUID(), email, Role.USER);
    }

    public static User admin(String email) {
        return comIdEPapel(UUID.randomUUID(), email, Role.ADMIN);
    }

    public static User comIdEPapel(UUID id, String email, Role papel) {
        User usuario = User.novoUsuarioComum(email, "$2a$10$hashirrelevanteparaestetestexxxxxxxxxxxxxxxxxxxxxxxxxxxx", "Fulano de Teste");
        ReflectionTestUtils.setField(usuario, "id", id);
        ReflectionTestUtils.setField(usuario, "role", papel);
        return usuario;
    }

    public static User desabilitado(String email) {
        User usuario = comum(email);
        ReflectionTestUtils.setField(usuario, "enabled", false);
        return usuario;
    }
}
