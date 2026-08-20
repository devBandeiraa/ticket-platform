package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devbandeiraa.bookingservice.domain.EventInventory;
import com.devbandeiraa.bookingservice.repository.EventInventoryRepository;
import com.devbandeiraa.bookingservice.support.PostgresContainerConfig;
import com.devbandeiraa.bookingservice.support.RedisContainerConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica a rede de seguranca do banco contra overselling.
 *
 * <p>Estes testes nao passam pela regra de negocio de proposito. A tese do projeto e que a
 * garantia contra vender ingresso a mais nao esta na aplicacao nem no lock distribuido, e sim no
 * PostgreSQL. Uma afirmacao dessas so vale se for verificada por fora da aplicacao — inclusive
 * escrevendo direto na tabela, como faria um script de correcao as pressas na madrugada.
 */
@SpringBootTest
@Import({PostgresContainerConfig.class, RedisContainerConfig.class})
@ActiveProfiles("test")
class EstoqueConstraintIntegrationTest {

    @Autowired
    private EventInventoryRepository estoqueRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("reserva dentro da capacidade e aceita")
    void deveReservarDentroDaCapacidade() {
        UUID eventoId = estoqueCom(10);

        assertThat(estoqueRepository.reservar(eventoId, 4)).isEqualTo(1);
        assertThat(recarregar(eventoId).getReservedTickets()).isEqualTo(4);
    }

    @Test
    @Transactional
    @DisplayName("reserva que estouraria a capacidade nao afeta linha nenhuma")
    void naoDeveReservarAcimaDaCapacidade() {
        UUID eventoId = estoqueCom(10);
        estoqueRepository.reservar(eventoId, 8);

        // Cabem 2, foram pedidos 5: a condicao do WHERE nao se satisfaz e o UPDATE nao acontece.
        assertThat(estoqueRepository.reservar(eventoId, 5)).isZero();
        assertThat(recarregar(eventoId).getReservedTickets()).isEqualTo(8);
    }

    @Test
    @Transactional
    @DisplayName("reservar exatamente o que resta e aceito")
    void deveReservarOUltimoIngresso() {
        UUID eventoId = estoqueCom(10);
        estoqueRepository.reservar(eventoId, 9);

        assertThat(estoqueRepository.reservar(eventoId, 1)).isEqualTo(1);
        assertThat(recarregar(eventoId).getDisponivel()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("devolucao nunca deixa o contador negativo")
    void naoDeveDevolverAlemDoReservado() {
        UUID eventoId = estoqueCom(10);
        estoqueRepository.reservar(eventoId, 3);

        // Simula uma devolucao repetida da mesma reserva: o piso zero recusa a segunda.
        assertThat(estoqueRepository.devolver(eventoId, 3)).isEqualTo(1);
        assertThat(estoqueRepository.devolver(eventoId, 3)).isZero();
        assertThat(recarregar(eventoId).getReservedTickets()).isZero();
    }

    @Test
    @DisplayName("o banco recusa overselling mesmo com UPDATE direto, fora da aplicacao")
    void bancoDeveRecusarEstadoInvalido() {
        UUID eventoId = estoqueCom(10);

        // Aqui nao ha regra de negocio, nem lock, nem repositorio: e SQL cru contra a tabela.
        // Se este teste falhar, a ultima rede de seguranca do projeto nao existe.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE event_inventory SET reserved_tickets = ? WHERE event_id = ?", 11, eventoId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE event_inventory SET reserved_tickets = ? WHERE event_id = ?", -1, eventoId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID estoqueCom(int capacidade) {
        UUID eventoId = UUID.randomUUID();
        estoqueRepository.saveAndFlush(
                EventInventory.hidratado(eventoId, capacidade, new BigDecimal("100.00")));
        return eventoId;
    }

    private EventInventory recarregar(UUID eventoId) {
        return estoqueRepository.findById(eventoId).orElseThrow();
    }
}
