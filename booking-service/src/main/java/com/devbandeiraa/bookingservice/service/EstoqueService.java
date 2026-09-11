package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.EventSnapshot;
import com.devbandeiraa.bookingservice.client.SectorSnapshot;
import com.devbandeiraa.bookingservice.domain.EventSeat;
import com.devbandeiraa.bookingservice.exception.EventoNaoDisponivelException;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Mantem o espelho local dos lugares.
 *
 * <p>A hidratacao e preguicosa: a planta de um evento so e copiada do event-service na primeira
 * vez que alguem tenta reserva-lo ou consultar sua disponibilidade. Copiar tudo antecipadamente
 * exigiria saber quando cada evento e publicado e manteria linhas para eventos que ninguem
 * procurou.
 *
 * <p>O que se copia sao as <strong>dimensoes</strong> dos setores, e o que se grava sao os
 * lugares que elas descrevem. O event-service guarda tres linhas; este servico guarda mil e
 * quinhentas — e a assimetria e o desenho, nao um descuido: e aqui que cada lugar tem estado.
 */
@Service
public class EstoqueService {

    private static final Logger log = LoggerFactory.getLogger(EstoqueService.class);

    /**
     * Tamanho do lote na gravacao dos assentos.
     *
     * <p>Uma casa de tres mil lugares vira tres mil linhas na primeira reserva. Uma instrucao
     * por linha faria tres mil idas ao banco e transformaria a primeira compra do evento numa
     * espera de dezenas de segundos.
     */
    private static final int TAMANHO_DO_LOTE = 500;

    private final EventSeatRepository assentoRepository;
    private final EventClient eventClient;
    private final JdbcTemplate jdbcTemplate;

    public EstoqueService(EventSeatRepository assentoRepository, EventClient eventClient,
                          JdbcTemplate jdbcTemplate) {
        this.assentoRepository = assentoRepository;
        this.eventClient = eventClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Garante que os lugares do evento existam localmente.
     *
     * <p>Deliberadamente <strong>nao</strong> anotado com {@code @Transactional}, e isso importa.
     * A hidratacao faz uma chamada de rede e pode esbarrar numa violacao de unicidade; se tudo
     * corresse dentro de uma transacao unica, a conexao ficaria aberta durante a chamada REST
     * e — pior — a violacao marcaria a transacao como somente-rollback, impedindo a releitura
     * logo abaixo. Sem transacao externa, cada operacao abre e fecha a sua, e a verificacao do
     * bloco catch enxerga o que a outra requisicao gravou.
     */
    public void garantirHidratado(UUID eventId) {
        if (assentoRepository.existsByEventId(eventId)) {
            return;
        }

        EventSnapshot evento = eventClient.buscarPublicado(eventId);

        List<EventSeat> assentos = gerarAssentos(eventId, evento.sectors());
        if (assentos.isEmpty()) {
            // Um evento publicado sem setor algum nao deveria existir — o event-service exige
            // ao menos um. Chegando aqui, o catalogo esta inconsistente, e vender lugar de uma
            // casa sem planta seria pior do que recusar.
            throw new EventoNaoDisponivelException(eventId);
        }

        try {
            gravarEmLotes(assentos);
            log.info("planta hidratada: evento={} setores={} lugares={}",
                    eventId, evento.sectors().size(), assentos.size());

        } catch (DataIntegrityViolationException outroChegouPrimeiro) {
            // Duas primeiras reservas do mesmo evento chegando juntas: ambas passaram pela
            // verificacao sem achar nada e tentaram gravar. A unicidade da chave natural
            // garante que apenas uma completa; a outra apenas segue, porque os lugares de que
            // ela precisa ja estao la.
            log.debug("evento {} hidratado em paralelo por outra requisicao", eventId);

            if (!assentoRepository.existsByEventId(eventId)) {
                throw outroChegouPrimeiro;
            }
        }
    }

    /**
     * Transforma as dimensoes dos setores em lugares.
     *
     * <p>Os rotulos das filas vem prontos do event-service, e nao sao recalculados aqui — ver a
     * nota em {@code SectorSnapshot}.
     */
    private static List<EventSeat> gerarAssentos(UUID eventId, List<SectorSnapshot> setores) {
        List<EventSeat> assentos = new ArrayList<>();

        for (SectorSnapshot setor : setores) {
            for (String fila : setor.rowLabels()) {
                for (int numero = 1; numero <= setor.seatsPerRow(); numero++) {
                    assentos.add(EventSeat.livre(eventId, setor.name(), fila, numero, setor.price()));
                }
            }
        }

        return assentos;
    }

    /**
     * Grava os lugares em lotes, por JDBC.
     *
     * <p>Por JDBC e nao pelo repositorio: o {@code saveAll} do Spring Data percorre entidade por
     * entidade e as mantem no contexto de persistencia, o que para tres mil linhas custa memoria
     * e tempo sem oferecer nada — nenhuma delas sera lida ou alterada em seguida. Aqui a
     * insercao e um fato unico, e a ferramenta certa e o lote.
     */
    private void gravarEmLotes(List<EventSeat> assentos) {
        String sql = """
                INSERT INTO event_seats
                    (id, event_id, sector_name, row_label, seat_number, price, status)
                VALUES (?, ?, ?, ?, ?, ?, 'FREE')
                """;

        for (int inicio = 0; inicio < assentos.size(); inicio += TAMANHO_DO_LOTE) {
            List<EventSeat> lote =
                    assentos.subList(inicio, Math.min(inicio + TAMANHO_DO_LOTE, assentos.size()));

            jdbcTemplate.batchUpdate(sql, lote, lote.size(), (PreparedStatement ps, EventSeat a) -> {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, a.getEventId());
                ps.setString(3, a.getSectorName());
                ps.setString(4, a.getRowLabel());
                ps.setInt(5, a.getSeatNumber());
                ps.setBigDecimal(6, a.getPrice());
            });
        }
    }

    /** Capacidade e disponibilidade do evento, contadas sobre os proprios lugares. */
    public Disponibilidade consultar(UUID eventId) {
        garantirHidratado(eventId);

        long total = assentoRepository.countByEventId(eventId);
        long livres = assentoRepository.contarLivres(eventId);

        return new Disponibilidade(eventId, total, total - livres, livres);
    }

    /**
     * Quantos lugares o evento tem, e quantos restam.
     *
     * <p>Contados, e nao lidos de um contador. Um contador de reservados ao lado de uma linha
     * por assento seriam duas fontes de verdade para a mesma pergunta, e duas fontes divergem —
     * foi por isso que {@code event_inventory} deixou de existir.
     */
    public record Disponibilidade(UUID eventId, long total, long reservados, long disponiveis) {
    }

    /** O mapa do evento, hidratando-o se for a primeira visita. */
    public List<EventSeat> mapaDe(UUID eventId) {
        garantirHidratado(eventId);
        return assentoRepository.findByEventIdOrderBySectorNameAscRowLabelAscSeatNumberAsc(eventId);
    }

    /** Preco de um conjunto de lugares, para a soma da reserva. */
    public static BigDecimal somar(List<EventSeat> assentos) {
        return assentos.stream()
                .map(EventSeat::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
