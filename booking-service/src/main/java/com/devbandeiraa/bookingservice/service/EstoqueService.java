package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.EventSnapshot;
import com.devbandeiraa.bookingservice.domain.EventInventory;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Mantem o espelho local do estoque.
 *
 * <p>A hidratacao e preguicosa: o estoque de um evento so e copiado do event-service na primeira
 * vez que alguem tenta reserva-lo. Copiar tudo antecipadamente exigiria saber quando cada evento
 * e publicado e manteria linhas para eventos que ninguem procurou.
 */
@Service
public class EstoqueService {

    private static final Logger log = LoggerFactory.getLogger(EstoqueService.class);

    private final EventInventoryRepository estoqueRepository;
    private final EventClient eventClient;

    public EstoqueService(EventInventoryRepository estoqueRepository, EventClient eventClient) {
        this.estoqueRepository = estoqueRepository;
        this.eventClient = eventClient;
    }

    /**
     * Devolve o estoque do evento, hidratando-o do event-service se ainda nao existir.
     *
     * <p>Deliberadamente <strong>nao</strong> anotado com {@code @Transactional}, e isso importa.
     * A hidratacao faz uma chamada de rede e pode esbarrar numa violacao de chave primaria; se
     * tudo corresse dentro de uma transacao unica, a conexao ficaria aberta durante a chamada
     * REST e — pior — a violacao marcaria a transacao como somente-rollback, impedindo a
     * releitura logo abaixo. Sem transacao externa, cada operacao do repositorio abre e fecha a
     * sua, e o {@code findById} do bloco catch enxerga o que a outra requisicao gravou.
     */
    public EventInventory garantirHidratado(UUID eventId) {
        Optional<EventInventory> jaHidratado = estoqueRepository.findById(eventId);
        if (jaHidratado.isPresent()) {
            return jaHidratado.get();
        }

        EventSnapshot evento = eventClient.buscarPublicado(eventId);

        try {
            EventInventory estoque = estoqueRepository.saveAndFlush(
                    EventInventory.hidratado(eventId, evento.totalTickets(), evento.price()));

            log.info("estoque hidratado: evento={} capacidade={} preco={}",
                    eventId, evento.totalTickets(), evento.price());

            return estoque;

        } catch (DataIntegrityViolationException outroChegouPrimeiro) {
            // Duas primeiras reservas do mesmo evento chegando juntas: ambas passaram pelo
            // findById sem achar nada e tentaram inserir. A chave primaria garante que apenas
            // uma grava; a outra apenas le o que acabou de ser gravado, e segue normalmente.
            log.debug("evento {} hidratado em paralelo por outra requisicao", eventId);

            return estoqueRepository.findById(eventId)
                    .orElseThrow(() -> outroChegouPrimeiro);
        }
    }
}
