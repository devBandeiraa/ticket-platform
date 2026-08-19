package com.devbandeiraa.authservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devbandeiraa.authservice.domain.Role;
import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.dto.request.RegisterRequest;
import com.devbandeiraa.authservice.exception.EmailAlreadyRegisteredException;
import com.devbandeiraa.authservice.repository.UserRepository;
import com.devbandeiraa.authservice.service.UserRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Testes de unidade da regra de cadastro, sem contexto Spring e sem banco. */
@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    private static final String SENHA_EM_CLARO = "senhaSegura123";
    private static final String HASH_FALSO = "$2a$10$hashfalsoparateste000000000000000000000000000000000000";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserRegistrationService service;

    @Captor
    private ArgumentCaptor<User> capturadorDeUsuario;

    @Test
    @DisplayName("persiste o hash da senha, nunca a senha em claro")
    void devePersistirApenasOHashDaSenha() {
        prepararCadastroBemSucedido();

        service.registrar(new RegisterRequest("joao@email.com", SENHA_EM_CLARO, "Joao Silva"));

        verify(userRepository).saveAndFlush(capturadorDeUsuario.capture());
        User persistido = capturadorDeUsuario.getValue();

        assertThat(persistido.getPasswordHash()).isEqualTo(HASH_FALSO);
        assertThat(persistido.getPasswordHash()).isNotEqualTo(SENHA_EM_CLARO);
    }

    @Test
    @DisplayName("normaliza o e-mail para minusculas e sem espacos")
    void deveNormalizarEmail() {
        prepararCadastroBemSucedido();

        service.registrar(new RegisterRequest("  Joao@Email.COM  ", SENHA_EM_CLARO, "Joao Silva"));

        verify(userRepository).saveAndFlush(capturadorDeUsuario.capture());
        assertThat(capturadorDeUsuario.getValue().getEmail()).isEqualTo("joao@email.com");
    }

    @Test
    @DisplayName("todo cadastro publico nasce como USER, nunca como ADMIN")
    void deveCadastrarSempreComoUsuarioComum() {
        prepararCadastroBemSucedido();

        service.registrar(new RegisterRequest("joao@email.com", SENHA_EM_CLARO, "Joao Silva"));

        verify(userRepository).saveAndFlush(capturadorDeUsuario.capture());
        assertThat(capturadorDeUsuario.getValue().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("rejeita e-mail ja cadastrado sem chegar a tocar no banco")
    void deveRejeitarEmailJaCadastrado() {
        when(userRepository.existsByEmail("joao@email.com")).thenReturn(true);

        assertThatThrownBy(() ->
                service.registrar(new RegisterRequest("joao@email.com", SENHA_EM_CLARO, "Joao Silva")))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessageContaining("joao@email.com");

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("traduz violacao da constraint em conflito, cobrindo o cadastro simultaneo")
    void deveTraduzirViolacaoDeUnicidadeEmConflito() {
        // Simula a corrida: o existsByEmail passa, mas outra transacao gravou o mesmo
        // e-mail no intervalo e a unique constraint do banco barra este INSERT.
        when(userRepository.existsByEmail("joao@email.com")).thenReturn(false);
        when(passwordEncoder.encode(SENHA_EM_CLARO)).thenReturn(HASH_FALSO);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() ->
                service.registrar(new RegisterRequest("joao@email.com", SENHA_EM_CLARO, "Joao Silva")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    private void prepararCadastroBemSucedido() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(SENHA_EM_CLARO)).thenReturn(HASH_FALSO);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(chamada -> chamada.getArgument(0));
    }
}
