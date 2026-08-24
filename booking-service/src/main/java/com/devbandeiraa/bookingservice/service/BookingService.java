package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.client.Autorizacao;
import com.devbandeiraa.bookingservice.client.PagamentoClient;
import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.domain.EventInventory;
import com.devbandeiraa.bookingservice.dto.request.CreateBookingRequest;
import com.devbandeiraa.bookingservice.dto.response.BookingResponse;
import com.devbandeiraa.bookingservice.dto.response.PaginaResponse;
import com.devbandeiraa.bookingservice.exception.ChaveDeIdempotenciaInvalidaException;
import com.devbandeiraa.bookingservice.exception.ReservaNaoEncontradaException;
import com.devbandeiraa.bookingservice.exception.TransicaoDeReservaInvalidaException;
import com.devbandeiraa.bookingservice.lock.DistributedLock;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.BookingSpecifications;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra de negocio da reserva.
 *
 * <p>Esta classe orquestra; ela nao e transacional. A transacao vive em
 * {@link ReservaTransacional}, dentro do lock, e a razao esta documentada la.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    /**
     * O lock e por evento, e nao global. Reservar para o show A nao concorre com reservar para o
     * show B: um lock unico serializaria a plataforma inteira em uma fila so, transformando o
     * remedio para a corrida em gargalo de todo o sistema.
     */
    private static final String PREFIXO_DA_CHAVE_DE_LOCK = "lock:event:";

    private final BookingRepository bookingRepository;
    private final EventInventoryRepository estoqueRepository;
    private final EstoqueService estoqueService;
    private final ReservaTransacional reservaTransacional;
    private final ConfirmacaoTransacional confirmacaoTransacional;
    private final PagamentoClient pagamentoClient;
    private final DistributedLock lock;
    private final ReservaProperties propriedades;

    public BookingService(BookingRepository bookingRepository,
                          EventInventoryRepository estoqueRepository,
                          EstoqueService estoqueService,
                          ReservaTransacional reservaTransacional,
                          ConfirmacaoTransacional confirmacaoTransacional,
                          PagamentoClient pagamentoClient,
                          DistributedLock lock,
                          ReservaProperties propriedades) {
        this.bookingRepository = bookingRepository;
        this.estoqueRepository = estoqueRepository;
        this.estoqueService = estoqueService;
        this.reservaTransacional = reservaTransacional;
        this.confirmacaoTransacional = confirmacaoTransacional;
        this.pagamentoClient = pagamentoClient;
        this.lock = lock;
        this.propriedades = propriedades;
    }

    /**
     * Cria a reserva, segurando o estoque ate o prazo de pagamento.
     *
     * <p>Quatro etapas, nesta ordem, e a ordem importa:
     *
     * <ol>
     *   <li><strong>Idempotencia.</strong> Se a chave ja produziu uma reserva, devolve aquela.
     *       Um cliente que perdeu a resposta por timeout e repetiu o pedido nao pode acabar com
     *       duas reservas.
     *   <li><strong>Hidratacao.</strong> Garante o estoque local, chamando o event-service se
     *       for a primeira reserva daquele evento. Fica <em>fora</em> do lock de proposito: e
     *       uma chamada de rede, e segurar o lock durante ela desperdicaria boa parte do TTL
     *       esperando um servico externo.
     *   <li><strong>Secao critica.</strong> Sob o lock, toma o estoque e grava a reserva na
     *       mesma transacao.
     *   <li><strong>Colisao de idempotencia.</strong> Duas requisicoes identicas em paralelo
     *       passam as duas pela etapa 1 sem encontrar nada; a constraint de unicidade recusa a
     *       segunda, que entao devolve a reserva da primeira.
     * </ol>
     */
    public ResultadoDaReserva criar(CreateBookingRequest requisicao, UUID usuarioId, String chave) {
        String chaveDeIdempotencia = validarChave(chave);

        Optional<Booking> jaReservada =
                bookingRepository.findByUserIdAndIdempotencyKey(usuarioId, chaveDeIdempotencia);
        if (jaReservada.isPresent()) {
            log.debug("requisicao repetida com a chave '{}': devolvendo a reserva {}",
                    chaveDeIdempotencia, jaReservada.get().getId());
            return ResultadoDaReserva.repetida(BookingResponse.de(jaReservada.get()));
        }

        EventInventory estoque = estoqueService.garantirHidratado(requisicao.eventId());
        Instant expiraEm = Instant.now().plus(propriedades.ttl());

        try {
            Booking reserva = lock.executarComLock(
                    PREFIXO_DA_CHAVE_DE_LOCK + requisicao.eventId(),
                    () -> reservaTransacional.registrar(
                            requisicao.eventId(),
                            usuarioId,
                            requisicao.quantity(),
                            estoque.getPrice(),
                            expiraEm,
                            chaveDeIdempotencia));

            log.info("reserva criada: id={} evento={} usuario={} quantidade={} expira={}",
                    reserva.getId(), reserva.getEventId(), usuarioId,
                    reserva.getQuantity(), reserva.getExpiresAt());

            return ResultadoDaReserva.criada(BookingResponse.de(reserva));

        } catch (DataIntegrityViolationException chaveRepetidaEmParalelo) {
            // A transacao ja fez rollback, devolvendo o estoque que havia tomado. Resta apenas
            // localizar a reserva que a requisicao vencedora gravou com a mesma chave.
            return bookingRepository
                    .findByUserIdAndIdempotencyKey(usuarioId, chaveDeIdempotencia)
                    .map(reserva -> {
                        log.debug("colisao de idempotencia na chave '{}': devolvendo a reserva {}",
                                chaveDeIdempotencia, reserva.getId());
                        return ResultadoDaReserva.repetida(BookingResponse.de(reserva));
                    })
                    // Se nao ha reserva com esta chave, a violacao foi de outra constraint e nao
                    // deve ser confundida com idempotencia.
                    .orElseThrow(() -> chaveRepetidaEmParalelo);
        }
    }

    /**
     * Busca uma reserva.
     *
     * <p>Um administrador enxerga qualquer reserva; um usuario comum, apenas as suas. A
     * verificacao acontece aqui, e nao no controller, para que qualquer caminho futuro que leia
     * uma reserva passe pela mesma regra.
     */
    @Transactional(readOnly = true)
    public BookingResponse buscar(UUID id, AuthenticatedUser solicitante) {
        return BookingResponse.de(carregarVisivelPara(id, solicitante));
    }

    /** Reservas do usuario autenticado, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public PaginaResponse<BookingResponse> listarDoUsuario(UUID usuarioId, Pageable pageable) {
        return PaginaResponse.de(
                bookingRepository.findByUserId(usuarioId, pageable), BookingResponse::de);
    }

    /** Listagem administrativa, com filtros opcionais que se combinam. */
    @Transactional(readOnly = true)
    public PaginaResponse<BookingResponse> listarParaAdmin(
            UUID eventId, BookingStatus status, Pageable pageable) {

        Specification<Booking> filtro = Specification.allOf();
        if (eventId != null) {
            filtro = filtro.and(BookingSpecifications.doEvento(eventId));
        }
        if (status != null) {
            filtro = filtro.and(BookingSpecifications.comStatus(status));
        }

        return PaginaResponse.de(bookingRepository.findAll(filtro, pageable), BookingResponse::de);
    }

    // ---------- transicoes de estado ----------

    /**
     * Pagamento: cobra do provedor externo e, se a cobranca passar, leva a reserva de
     * {@code PENDING} a {@code CONFIRMED}.
     *
     * <p>Tres etapas, e a ordem e o assunto inteiro deste metodo:
     *
     * <ol>
     *   <li><strong>Recusa antecipada.</strong> Reserva ja paga devolve a si mesma sem cobrar de
     *       novo; reserva que nao esta mais pendente falha antes de qualquer cobranca. Nao elimina
     *       a corrida com o job de expiracao — apenas evita cobrar no caso, comum, em que ja se
     *       sabe que a confirmacao nao vai acontecer.
     *   <li><strong>Cobranca, fora de transacao.</strong> Ver {@link ConfirmacaoTransacional} para
     *       o motivo: uma chamada de rede com retry dentro de uma transacao seguraria uma conexao
     *       do pool por segundos, e sob carga isso derruba o banco para todo mundo.
     *   <li><strong>Confirmacao condicional.</strong> O {@code UPDATE} exige status pendente
     *       <em>e</em> prazo valido, e continua sendo quem decide a corrida com o job.
     * </ol>
     *
     * <h2>A janela que sobra</h2>
     *
     * <p>Entre a cobranca e a confirmacao existe um intervalo real, da duracao da chamada ao
     * provedor. Se o prazo vencer exatamente ali, o dinheiro saiu e a reserva nao pode mais ser
     * confirmada. Fechar essa janela de vez exigiria um estado {@code PAYING} e um saga completo;
     * o caminho escolhido foi compensar — estornar a cobranca e recusar o pagamento — que resolve
     * o caso concreto sem uma maquina de estados a mais.
     */
    public BookingResponse pagar(UUID id, AuthenticatedUser solicitante) {
        Booking reserva = carregarVisivelPara(id, solicitante);

        if (reserva.getStatus() == BookingStatus.CONFIRMED) {
            // Duplo clique no botao de pagar. Devolver a reserva sem chamar o provedor evita uma
            // ida a rede que o provedor descartaria de todo modo pela chave de idempotencia.
            log.debug("pagamento repetido da reserva {}: ja estava confirmada", id);
            return BookingResponse.de(reserva);
        }

        if (!reserva.estaPendente()) {
            return recusarPagamento(id);
        }

        Autorizacao autorizacao = pagamentoClient.autorizar(id, reserva.getTotalPrice());

        return confirmacaoTransacional.confirmar(id, autorizacao)
                .orElseGet(() -> estornarERecusar(id, autorizacao));
    }

    /**
     * A cobranca passou, mas a reserva ja nao podia mais ser confirmada.
     *
     * <p>O job de expiracao venceu a corrida durante a chamada ao provedor. Deixar assim
     * significaria dinheiro cobrado sem ingresso, que e bem pior do que a venda perdida: o estorno
     * vem primeiro, e so depois a recusa e devolvida ao usuario.
     *
     * <p>O estorno e melhor esforco de proposito. Se ele tambem falhar, o comprovante fica
     * registrado em log como pendencia — prender a resposta do usuario tentando de novo nao
     * mudaria o que ele ve, e fingir que o estorno sempre funciona seria pior que admitir o caso.
     */
    private BookingResponse estornarERecusar(UUID id, Autorizacao autorizacao) {
        Booking atual = recarregar(id);

        // Antes de estornar, e preciso descartar um segundo motivo para o UPDATE ter afetado zero
        // linhas: outra requisicao de pagamento da MESMA reserva ter confirmado primeiro.
        //
        // O detalhe que torna isso perigoso e a propria idempotencia. Como a chave deriva do id da
        // reserva, as duas requisicoes concorrentes receberam o MESMO comprovante do provedor —
        // entao estornar aqui cancelaria exatamente a cobranca que acabou de pagar o ingresso,
        // deixando uma reserva confirmada e sem pagamento.
        if (atual.getStatus() == BookingStatus.CONFIRMED) {
            log.debug("reserva {} confirmada por uma requisicao concorrente; nada a estornar", id);
            return BookingResponse.de(atual);
        }

        log.warn("reserva {} nao pode mais ser confirmada: estornando o comprovante {}",
                id, autorizacao.authorizationCode());

        pagamentoClient.estornar(id, autorizacao.authorizationCode());

        return recusarPagamento(id);
    }

    /**
     * Cancelamento pedido pelo usuario, devolvendo o estoque.
     *
     * <p>A devolucao so acontece se o {@code UPDATE} tiver afetado uma linha. Como apenas uma
     * chamada consegue tirar a reserva de {@code PENDING}, o estoque volta exatamente uma vez,
     * mesmo que o usuario clique duas vezes ou que o job de expiracao esteja rodando junto.
     */
    @Transactional
    public void cancelar(UUID id, AuthenticatedUser solicitante) {
        Booking reserva = carregarVisivelPara(id, solicitante);

        if (bookingRepository.cancelar(id) == 0) {
            recusarCancelamento(id);
            return;
        }

        estoqueRepository.devolver(reserva.getEventId(), reserva.getQuantity());
        log.info("reserva cancelada: id={} evento={} quantidade={} (estoque devolvido)",
                id, reserva.getEventId(), reserva.getQuantity());
    }

    /**
     * Expira uma reserva vencida, devolvendo o estoque.
     *
     * <p>Chamada pelo job, uma reserva por transacao. Em lote unico, uma unica reserva
     * problematica desfaria o trabalho de todas as outras a cada rodada — e, como a varredura e
     * ordenada por vencimento, a mesma reserva voltaria a bloquear a proxima passada
     * indefinidamente.
     *
     * @return {@code true} se esta chamada foi a que expirou a reserva
     */
    @Transactional
    public boolean expirar(Booking reserva) {
        if (bookingRepository.expirar(reserva.getId()) == 0) {
            // Alguem pagou ou cancelou entre a varredura e este UPDATE. Nao ha o que fazer, e
            // sobretudo nao ha estoque a devolver: quem fez a transicao ja cuidou disso.
            return false;
        }

        estoqueRepository.devolver(reserva.getEventId(), reserva.getQuantity());
        log.info("reserva expirada: id={} evento={} quantidade={} (estoque devolvido)",
                reserva.getId(), reserva.getEventId(), reserva.getQuantity());

        return true;
    }

    // ---------- apoio ----------

    /**
     * Explica por que o pagamento nao passou, consultando o estado que o banco encontrou.
     *
     * <p>Devolver um {@code 409} generico bastaria para a corretude, mas nao para o frontend:
     * "expirou" pede um botao de reservar de novo, "cancelada" pede uma explicacao, e "ja paga"
     * pede o comprovante.
     */
    private BookingResponse recusarPagamento(UUID id) {
        Booking atual = recarregar(id);

        if (atual.getStatus() == BookingStatus.CONFIRMED) {
            log.debug("pagamento repetido da reserva {}: ja estava confirmada", id);
            return BookingResponse.de(atual);
        }

        throw switch (atual.getStatus()) {
            case CANCELLED -> new TransicaoDeReservaInvalidaException(
                    id, "BOOKING_CANCELLED", "a reserva foi cancelada e nao pode ser paga");
            // PENDING aqui significa que o prazo venceu mas o job ainda nao passou. Do ponto de
            // vista do usuario o desfecho e o mesmo de EXPIRED, e a resposta tambem deve ser.
            default -> new TransicaoDeReservaInvalidaException(
                    id, "BOOKING_EXPIRED", "o prazo de pagamento venceu");
        };
    }

    private void recusarCancelamento(UUID id) {
        Booking atual = recarregar(id);

        if (atual.getStatus() == BookingStatus.CANCELLED) {
            log.debug("cancelamento repetido da reserva {}: ja estava cancelada", id);
            return;
        }

        throw switch (atual.getStatus()) {
            // Cancelar uma reserva ja paga seria estorno, que envolve devolver dinheiro e nao
            // apenas estoque. Esta fora do escopo do pagamento simulado.
            case CONFIRMED -> new TransicaoDeReservaInvalidaException(
                    id, "BOOKING_ALREADY_CONFIRMED",
                    "a reserva ja foi paga; cancelamento apos o pagamento exige estorno");
            default -> new TransicaoDeReservaInvalidaException(
                    id, "BOOKING_EXPIRED", "a reserva ja havia expirado");
        };
    }

    /** Um admin enxerga qualquer reserva; um usuario comum, apenas as suas. */
    private Booking carregarVisivelPara(UUID id, AuthenticatedUser solicitante) {
        return bookingRepository.findById(id)
                .filter(reserva -> solicitante.isAdmin() || reserva.pertenceA(solicitante.id()))
                .orElseThrow(() -> new ReservaNaoEncontradaException(id));
    }

    private Booking recarregar(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new ReservaNaoEncontradaException(id));
    }

    /**
     * A chave e do cliente, e o servidor apenas a valida.
     *
     * <p>Gerar uma chave quando ela falta anularia a idempotencia: cada tentativa receberia uma
     * chave nova, e o retry criaria uma segunda reserva em vez de reencontrar a primeira.
     */
    private String validarChave(String chave) {
        if (chave == null || chave.isBlank()) {
            throw new ChaveDeIdempotenciaInvalidaException("o cabecalho e obrigatorio");
        }

        String normalizada = chave.trim();
        if (normalizada.length() > propriedades.tamanhoMaximoDaChave()) {
            throw new ChaveDeIdempotenciaInvalidaException(
                    "tamanho maximo de %d caracteres".formatted(propriedades.tamanhoMaximoDaChave()));
        }

        return normalizada;
    }
}
