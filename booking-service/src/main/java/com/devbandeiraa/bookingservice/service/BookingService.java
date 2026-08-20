package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.EventInventory;
import com.devbandeiraa.bookingservice.dto.request.CreateBookingRequest;
import com.devbandeiraa.bookingservice.dto.response.BookingResponse;
import com.devbandeiraa.bookingservice.dto.response.PaginaResponse;
import com.devbandeiraa.bookingservice.exception.ChaveDeIdempotenciaInvalidaException;
import com.devbandeiraa.bookingservice.exception.ReservaNaoEncontradaException;
import com.devbandeiraa.bookingservice.lock.DistributedLock;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
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
    private final EstoqueService estoqueService;
    private final ReservaTransacional reservaTransacional;
    private final DistributedLock lock;
    private final ReservaProperties propriedades;

    public BookingService(BookingRepository bookingRepository,
                          EstoqueService estoqueService,
                          ReservaTransacional reservaTransacional,
                          DistributedLock lock,
                          ReservaProperties propriedades) {
        this.bookingRepository = bookingRepository;
        this.estoqueService = estoqueService;
        this.reservaTransacional = reservaTransacional;
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
        Booking reserva = bookingRepository.findById(id)
                .filter(encontrada -> solicitante.isAdmin() || encontrada.pertenceA(solicitante.id()))
                .orElseThrow(() -> new ReservaNaoEncontradaException(id));

        return BookingResponse.de(reserva);
    }

    /** Reservas do usuario autenticado, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public PaginaResponse<BookingResponse> listarDoUsuario(UUID usuarioId, Pageable pageable) {
        return PaginaResponse.de(
                bookingRepository.findByUserId(usuarioId, pageable), BookingResponse::de);
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
