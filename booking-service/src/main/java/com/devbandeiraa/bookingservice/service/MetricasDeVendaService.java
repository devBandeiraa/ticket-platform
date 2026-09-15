package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.dto.response.MetricasResponse;
import com.devbandeiraa.bookingservice.repository.BookingRepository;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Totais de venda, lidos do banco.
 *
 * <p>Nao confundir com {@code MetricasDeNegocio}, que instrumenta o Micrometer. As duas medem
 * coisas diferentes: aquela conta o que ESTE processo observou desde que subiu e zera a cada
 * reinicio, servindo a alerta e a painel de operacao; esta responde "quanto ja vendemos", que so
 * o banco sabe. Uma nao substitui a outra.
 */
@Service
public class MetricasDeVendaService {

    private final BookingRepository bookingRepository;
    private final EventSeatRepository assentoRepository;

    public MetricasDeVendaService(BookingRepository bookingRepository,
                                  EventSeatRepository assentoRepository) {
        this.bookingRepository = bookingRepository;
        this.assentoRepository = assentoRepository;
    }

    /**
     * Agrega as duas tabelas.
     *
     * <p>Duas consultas, e nao uma com juncao: reservas e assentos respondem perguntas
     * independentes, e uni-las exigiria um join que multiplicaria linhas — cada reserva vezes
     * cada assento — so para depois desfazer a multiplicacao com {@code DISTINCT}.
     *
     * <p>{@code readOnly} para o driver saber que nao havera escrita. Alem de dispensar o
     * flush no fim, permite que a consulta va para uma replica de leitura no dia em que houver
     * uma, sem que este codigo mude.
     *
     * <p>As duas leituras ocorrem na mesma transacao, entao enxergam o mesmo instante. Em
     * transacoes separadas, uma reserva confirmada entre as duas apareceria como ingresso
     * vendido E como lugar disponivel — um painel se contradizendo sozinho.
     */
    @Transactional(readOnly = true)
    public MetricasResponse agregar() {
        return MetricasResponse.de(
                bookingRepository.agregarVendas(),
                assentoRepository.contarLivresEmTodosOsEventos());
    }
}
