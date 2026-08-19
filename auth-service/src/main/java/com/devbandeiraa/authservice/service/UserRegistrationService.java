package com.devbandeiraa.authservice.service;

import com.devbandeiraa.authservice.domain.User;
import com.devbandeiraa.authservice.dto.request.RegisterRequest;
import com.devbandeiraa.authservice.exception.EmailAlreadyRegisteredException;
import com.devbandeiraa.authservice.repository.UserRepository;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regra de negocio do cadastro de usuarios. */
@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserRegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Cadastra um novo usuario comum.
     *
     * @throws EmailAlreadyRegisteredException se o e-mail ja estiver em uso
     */
    @Transactional
    public User registrar(RegisterRequest requisicao) {
        String email = normalizarEmail(requisicao.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User usuario = User.novoUsuarioComum(
                email,
                passwordEncoder.encode(requisicao.password()),
                requisicao.fullName().trim());

        try {
            // saveAndFlush em vez de save para que a violacao de unicidade estoure aqui
            // dentro, e nao no commit da transacao, fora do alcance deste try.
            User salvo = userRepository.saveAndFlush(usuario);
            log.info("usuario cadastrado: id={} email={}", salvo.getId(), salvo.getEmail());
            return salvo;

        } catch (DataIntegrityViolationException excecao) {
            // Dois cadastros simultaneos com o mesmo e-mail atravessam o existsByEmail acima
            // lado a lado; quem perde a corrida esbarra na unique constraint. E o mesmo padrao
            // que protege o estoque de ingressos: a checagem previa evita o caso comum, a
            // constraint do banco e que garante a invariante.
            log.warn("corrida no cadastro do e-mail {}, resolvida pela constraint", email);
            throw new EmailAlreadyRegisteredException(email);
        }
    }

    /**
     * Normaliza o e-mail antes de gravar ou comparar. Sem isso "Joao@Email.com" e
     * "joao@email.com" virariam contas distintas, e o login ficaria sensivel a maiusculas.
     */
    private String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
