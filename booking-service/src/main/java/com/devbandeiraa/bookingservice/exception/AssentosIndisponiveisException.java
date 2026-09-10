package com.devbandeiraa.bookingservice.exception;

import java.util.List;
import java.util.UUID;

/**
 * Disparada quando ao menos um dos lugares escolhidos ja nao estava livre.
 *
 * <p>Distinta de {@link EstoqueEsgotadoException}, e a distincao importa para quem esta na tela.
 * "Esgotado" significa que nao ha mais lugar nenhum e nao adianta tentar de novo; isto aqui
 * significa que <em>estes</em> lugares sairam enquanto a pessoa decidia, e que escolher outros
 * resolve. Um codigo unico para os dois casos faria a tela dar o conselho errado em metade das
 * vezes.
 *
 * <p>Carrega as etiquetas dos lugares disputados para que o mapa possa dizer quais eram, em vez
 * de apenas piscar em vermelho.
 */
public class AssentosIndisponiveisException extends RuntimeException {

    private final transient List<String> assentos;

    public AssentosIndisponiveisException(UUID eventId, List<String> assentos) {
        super(assentos.isEmpty()
                ? "Um ou mais lugares escolhidos nao pertencem ao evento " + eventId
                : "Estes lugares ja nao estao disponiveis: " + String.join(", ", assentos));
        this.assentos = List.copyOf(assentos);
    }

    public List<String> getAssentos() {
        return assentos;
    }
}
