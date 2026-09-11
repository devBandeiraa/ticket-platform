package com.devbandeiraa.bookingservice.support;

import com.devbandeiraa.bookingservice.domain.EventSeat;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Monta e inspeciona o estado dos lugares nos testes.
 *
 * <p>Substitui o que antes era escrever direto no contador de estoque. Com assentos, "o evento
 * ja tem 30 lugares tomados" deixou de ser uma atribuicao e passou a ser um estado distribuido
 * por trinta linhas — e cada teste montar isso a mao seria repetir a mesma montagem em cinco
 * arquivos.
 */
public final class AssentosDeTeste {

    private AssentosDeTeste() {
    }

    /**
     * Cria os lugares de um evento sem passar pelo event-service.
     *
     * <p>Usado pelos testes que nao estao verificando a hidratacao e so precisam de uma casa
     * pronta. Quem verifica a hidratacao mocka o {@code EventClient} e deixa o servico gerar.
     */
    public static void criarCasa(EventSeatRepository repositorio, UUID eventoId, int capacidade,
                                 BigDecimal preco) {
        // saveAllAndFlush, e nao saveAll seguido de flush: fora de uma transacao aberta, o
        // flush avulso nao encontra EntityManager e estoura.
        repositorio.saveAllAndFlush(lugares(eventoId, capacidade, preco));
    }

    private static List<EventSeat> lugares(UUID eventoId, int capacidade, BigDecimal preco) {
        return IntStream.rangeClosed(1, capacidade)
                .mapToObj(numero -> EventSeat.livre(eventoId, "Plateia", "A", numero, preco))
                .toList();
    }

    /**
     * Toma {@code quantos} lugares em nome de outra reserva qualquer.
     *
     * <p>Reproduz "alguem ja levou estes lugares" antes do que o teste vai exercitar. Passa pelo
     * mesmo {@code UPDATE} condicional do codigo de producao, e nao por uma escrita direta: um
     * atalho aqui poderia montar um estado que a aplicacao nunca produziria.
     */
    public static void ocupar(EventSeatRepository repositorio, UUID eventoId, int quantos) {
        ocuparPara(repositorio, eventoId, quantos, UUID.randomUUID());
    }

    /**
     * Toma {@code quantos} lugares em nome de uma reserva especifica.
     *
     * <p>A distincao importa: liberar assentos e sempre "os desta reserva". Um fixture que tome
     * os lugares em nome de um id qualquer e depois crie a reserva com outro id monta um estado
     * que a aplicacao nunca produz — e o cancelamento nao libera nada, porque nao ha assento
     * apontando para aquela reserva.
     */
    public static void ocuparPara(EventSeatRepository repositorio, UUID eventoId, int quantos,
                                  UUID reservaId) {
        List<UUID> ids = repositorio.escolherMaisBaratosLivres(eventoId, quantos).stream()
                .map(EventSeat::getId)
                .toList();

        if (ids.size() < quantos) {
            throw new IllegalStateException(
                    "o evento nao tem %d lugares livres para ocupar".formatted(quantos));
        }

        repositorio.reservar(ids, eventoId, reservaId);
    }

    /** Quantos lugares do evento ja sairam de livre. */
    public static long ocupados(EventSeatRepository repositorio, UUID eventoId) {
        return repositorio.countByEventId(eventoId) - repositorio.contarLivres(eventoId);
    }

    /** Quantos lugares do evento ainda estao livres. */
    public static long livres(EventSeatRepository repositorio, UUID eventoId) {
        return repositorio.contarLivres(eventoId);
    }
}
