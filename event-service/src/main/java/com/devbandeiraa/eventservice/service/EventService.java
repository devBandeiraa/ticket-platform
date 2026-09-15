package com.devbandeiraa.eventservice.service;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.domain.EventCategory;
import com.devbandeiraa.eventservice.domain.EventStatus;
import com.devbandeiraa.eventservice.domain.LayoutDeSetor;
import com.devbandeiraa.eventservice.dto.request.EventRequest;
import com.devbandeiraa.eventservice.dto.response.EventDetailResponse;
import com.devbandeiraa.eventservice.dto.response.EventSummaryResponse;
import com.devbandeiraa.eventservice.dto.response.PaginaResponse;
import com.devbandeiraa.eventservice.exception.EventNotEditableException;
import com.devbandeiraa.eventservice.exception.EventNotFoundException;
import com.devbandeiraa.eventservice.exception.LayoutNaoAlteravelException;
import com.devbandeiraa.eventservice.repository.EventRepository;
import com.devbandeiraa.eventservice.repository.EventSpecifications;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regra de negocio do catalogo de eventos. */
@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // ---------- catalogo publico ----------

    /**
     * Listagem publica: apenas eventos publicados.
     *
     * <p>O filtro por {@code PUBLISHED} e aplicado aqui, e nao deixado a cargo do chamador, para
     * que nao exista caminho pelo qual um rascunho vaze para o publico por esquecimento de quem
     * chama.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<EventSummaryResponse> listarPublicados(
            String busca, EventCategory categoria, Instant de, Instant ate, Pageable pageable) {

        // Cada filtro so entra na consulta quando de fato veio preenchido. Alem de gerar um SQL
        // mais enxuto, evita o problema de tipagem do bind nulo no PostgreSQL — ver a nota em
        // EventSpecifications.
        Specification<Event> filtro = EventSpecifications.comStatus(EventStatus.PUBLISHED);

        String termo = normalizarBusca(busca);
        if (termo != null) {
            filtro = filtro.and(EventSpecifications.comNomeContendo(termo));
        }
        if (categoria != null) {
            filtro = filtro.and(EventSpecifications.naCategoria(categoria));
        }
        if (de != null) {
            filtro = filtro.and(EventSpecifications.aPartirDe(de));
        }
        if (ate != null) {
            filtro = filtro.and(EventSpecifications.ate(ate));
        }

        return PaginaResponse.de(eventRepository.findAll(filtro, pageable), EventSummaryResponse::de);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse buscarPublicado(UUID id) {
        Event evento = eventRepository.findByIdAndStatus(id, EventStatus.PUBLISHED)
                .orElseThrow(() -> new EventNotFoundException(id));

        return EventDetailResponse.de(evento);
    }

    // ---------- administracao ----------

    /** Diferente da listagem publica, o admin enxerga rascunhos e cancelados. */
    @Transactional(readOnly = true)
    public PaginaResponse<EventSummaryResponse> listarParaAdmin(EventStatus status, Pageable pageable) {
        Page<Event> pagina = status == null
                ? eventRepository.findAll(pageable)
                : eventRepository.findByStatus(status, pageable);

        return PaginaResponse.de(pagina, EventSummaryResponse::de);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse buscarParaAdmin(UUID id) {
        return EventDetailResponse.de(carregar(id));
    }

    @Transactional
    public EventDetailResponse criar(EventRequest requisicao, UUID adminId) {
        Event evento = Event.rascunho(
                requisicao.name(),
                requisicao.description(),
                requisicao.venue(),
                requisicao.eventDate(),
                requisicao.imageUrl(),
                requisicao.category(),
                adminId,
                layoutDe(requisicao));

        Event salvo = eventRepository.save(evento);
        log.info("evento criado: id={} nome='{}' por admin={}", salvo.getId(), salvo.getName(), adminId);

        return EventDetailResponse.de(salvo);
    }

    /**
     * Altera um evento.
     *
     * <p>Os dados de apresentacao mudam sempre; a planta da casa, so enquanto o evento e
     * rascunho. Publicado, ele pode ter reservas, e o booking-service ja copiou a capacidade
     * para o lado dele — mexer nos setores aqui mudaria a casa por baixo de quem ja comprou.
     *
     * <p>O layout enviado e comparado com o atual, e a recusa so acontece se a PLANTA de fato
     * mudou. Sem essa comparacao, editar apenas a descricao de um evento publicado seria
     * recusado: o mesmo record carrega os dois assuntos, e a tela reenvia os setores inalterados
     * junto.
     *
     * <p>Planta igual nao significa nada a fazer. Texto, beneficios e faixa do setor tambem
     * chegam nesse mesmo record e nao alteram lugar de ninguem, entao seguem editaveis depois da
     * publicacao — ver {@code mesmaPlanta} para o que entra em cada lado da divisao.
     */
    @Transactional
    public EventDetailResponse alterar(UUID id, EventRequest requisicao) {
        Event evento = carregarAlteravel(id);

        List<LayoutDeSetor> novoLayout = layoutDe(requisicao);
        if (mesmaPlanta(novoLayout, layoutAtualDe(evento))) {
            // Planta intacta: o que pode ter mudado e o que o setor diz de si, e isso nao
            // depende de o evento estar publicado. Ver Event.aplicarApresentacaoDosSetores.
            evento.aplicarApresentacaoDosSetores(novoLayout);
        } else {
            if (!evento.podeAlterarLayout()) {
                throw new LayoutNaoAlteravelException(id);
            }
            evento.aplicarLayout(novoLayout);
        }

        evento.alterarDados(
                requisicao.name(),
                requisicao.description(),
                requisicao.venue(),
                requisicao.eventDate(),
                requisicao.imageUrl(),
                requisicao.category());

        log.info("evento alterado: id={}", id);
        return EventDetailResponse.de(evento);
    }

    /**
     * Publica um evento, tornando-o visivel e vendavel.
     *
     * <p>Idempotente: publicar o que ja esta publicado nao e erro, apenas nao muda nada. Um duplo
     * clique no painel nao deve produzir uma mensagem de falha.
     */
    @Transactional
    public EventDetailResponse publicar(UUID id) {
        Event evento = carregarAlteravel(id);

        if (!evento.estaPublicado()) {
            evento.publicar();
            log.info("evento publicado: id={}", id);
        }

        return EventDetailResponse.de(evento);
    }

    /**
     * Cancela o evento.
     *
     * <p>Exclusao logica, e nao DELETE: reservas ja feitas apontam para este evento, e apagar o
     * registro as deixaria orfas, sem como explicar ao usuario a que evento se referiam.
     */
    @Transactional
    public void cancelar(UUID id) {
        Event evento = carregar(id);
        evento.cancelar();
        log.info("evento cancelado: id={}", id);
    }

    // ---------- apoio ----------

    private static List<LayoutDeSetor> layoutDe(EventRequest requisicao) {
        return requisicao.sectors().stream()
                .map(setor -> new LayoutDeSetor(
                        setor.name(), setor.price(), setor.rowsCount(), setor.seatsPerRow(),
                        setor.description(), setor.benefits(), setor.tier()))
                .toList();
    }

    private static List<LayoutDeSetor> layoutAtualDe(Event evento) {
        return evento.getSectors().stream()
                .map(setor -> new LayoutDeSetor(
                        setor.getName(), setor.getPrice(), setor.getRowsCount(),
                        setor.getSeatsPerRow(), setor.getDescription(), setor.getBenefits(),
                        setor.getTier()))
                .toList();
    }

    /**
     * Compara dois layouts campo a campo.
     *
     * <p>Compara a PLANTA: nome, dimensoes e preco. Descricao, beneficios e faixa ficam de fora
     * de proposito — sao apresentacao, nao alteram lugar de ninguem, e entram por
     * {@code aplicarApresentacaoDosSetores}, que vale tambem para evento publicado. Inclui-los
     * aqui faria um erro de digitacao em "Open bar" virar permanente no instante da publicacao.
     *
     * <p>Nao usa o {@code equals} do record, e a razao e o preco: {@code BigDecimal.equals} leva
     * a escala em conta, entao {@code 180.00} vindo do banco e {@code 180.0} vindo do JSON seriam
     * "diferentes". Um admin que editasse apenas a descricao de um evento publicado levaria um
     * {@code 409} por um layout que ninguem mudou — e o mesmo record carrega os dois assuntos,
     * entao a tela reenvia os setores junto de qualquer edicao. {@code compareTo} compara valor.
     */
    private static boolean mesmaPlanta(List<LayoutDeSetor> novo, List<LayoutDeSetor> atual) {
        if (novo.size() != atual.size()) {
            return false;
        }

        for (int i = 0; i < novo.size(); i++) {
            LayoutDeSetor a = novo.get(i);
            LayoutDeSetor b = atual.get(i);

            if (!a.name().equals(b.name())
                    || a.rowsCount() != b.rowsCount()
                    || a.seatsPerRow() != b.seatsPerRow()
                    || a.price().compareTo(b.price()) != 0) {
                return false;
            }
        }

        return true;
    }

    private Event carregar(UUID id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    private Event carregarAlteravel(UUID id) {
        Event evento = carregar(id);
        if (!evento.podeSerAlterado()) {
            throw new EventNotEditableException(id);
        }
        return evento;
    }

    /** Busca vazia ou so com espacos equivale a nao filtrar. */
    private String normalizarBusca(String busca) {
        return busca == null || busca.isBlank() ? null : busca.trim();
    }
}
