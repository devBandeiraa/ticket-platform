package com.devbandeiraa.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados do cadastro publico.
 *
 * <p>O papel do usuario nao entra aqui de proposito: aceita-lo no corpo da requisicao permitiria
 * que qualquer pessoa se cadastrasse como ADMIN. Todo cadastro publico nasce como USER.
 *
 * @param password limitado a 72 caracteres porque o BCrypt ignora silenciosamente o que passa
 *                 disso — aceitar mais daria ao usuario a falsa impressao de uma senha mais forte
 */
public record RegisterRequest(

        @NotBlank(message = "O e-mail e obrigatorio")
        @Email(message = "E-mail em formato invalido")
        @Size(max = 255, message = "O e-mail deve ter no maximo 255 caracteres")
        String email,

        @NotBlank(message = "A senha e obrigatoria")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
        String password,

        @NotBlank(message = "O nome e obrigatorio")
        @Size(max = 120, message = "O nome deve ter no maximo 120 caracteres")
        String fullName) {

    /**
     * Remove espacos em volta antes de qualquer validacao.
     *
     * <p>A Bean Validation roda sobre os campos ja construidos, entao sem esta limpeza um
     * e-mail colado com espaco sobrando ("  joao@email.com ") reprovaria na anotacao
     * {@code @Email} e o usuario levaria um 400 por um detalhe invisivel na tela.
     *
     * <p>Aqui so se trata de higiene do texto recebido. A normalizacao de negocio — deixar o
     * e-mail em minusculas para que a conta seja a mesma independente da caixa — pertence ao
     * servico, que e o dono dessa regra.
     *
     * <p>A senha fica de fora de proposito: espaco e um caractere valido dentro dela, e apara-la
     * mudaria silenciosamente a senha escolhida pelo usuario.
     */
    public RegisterRequest {
        email = aparar(email);
        fullName = aparar(fullName);
    }

    private static String aparar(String valor) {
        return valor == null ? null : valor.trim();
    }
}
