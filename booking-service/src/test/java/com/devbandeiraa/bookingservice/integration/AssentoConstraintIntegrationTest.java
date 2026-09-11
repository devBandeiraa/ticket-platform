package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devbandeiraa.bookingservice.domain.SeatStatus;
import com.devbandeiraa.bookingservice.repository.EventSeatRepository;
import com.devbandeiraa.bookingservice.support.AssentosDeTeste;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import java.math.BigDecimal;
import java.util.List;
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
 * Verifica a rede de seguranca do banco contra vender o mesmo lugar duas vezes.
 *
 * <p>Estes testes nao passam pela regra de negocio de proposito. A tese do projeto e que a
 * garantia nao esta na aplicacao nem no lock distribuido, e sim no PostgreSQL. Uma afirmacao
 * dessas so vale se for verificada por fora da aplicacao — inclusive escrevendo direto na
 * tabela, como faria um script de correcao as pressas na madrugada.
 *
 * <h2>O que mudou na Fase 17</h2>
 *
 * <p>Antes havia um contador e uma {@code CHECK (reserved <= total)}: nada na estrutura impedia
 * o numero de passar do teto, entao a regra precisava ser escrita como constraint.
 *
 * <p>Agora a invariante e <strong>estrutural</strong>. Existe uma linha por lugar, e ela so sai
 * de {@code FREE} uma vez. Vender o mesmo assento duas vezes exigiria duas linhas para o mesmo
 * assento — e e a unicidade da chave natural que nao permite. E o que estes testes verificam.
 */
// Transacional porque as consultas de tomada e liberacao sao @Modifying, e um flush sem
// transacao aberta nao encontra EntityManager. O SELECT ... FOR UPDATE tambem exige transacao
// para que o lock sobreviva alem da propria consulta.
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@Transactional
class AssentoConstraintIntegrationTest {

    private static final BigDecimal PRECO = new BigDecimal("100.00");

    @Autowired
    private EventSeatRepository assentoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("o mesmo lugar nao pode existir duas vezes, nem por INSERT direto")
    void naoDevePermitirLugarDuplicado() {
        UUID eventoId = casaCom(3);

        // Um INSERT que ignora a aplicacao inteira. Se ele passasse, existiriam duas linhas
        // para "Plateia A1" — e duas pessoas poderiam comprar o mesmo lugar sem que nenhum
        // UPDATE condicional percebesse, porque cada uma tomaria a sua linha.
        assertThatThrownBy(() -> inserirLugar(eventoId, "Plateia", "A", 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("um lugar so sai de livre uma vez: a segunda tomada afeta zero linhas")
    void soUmaTomadaDeveVencer() {
        UUID eventoId = casaCom(3);
        List<UUID> primeiro = List.of(algumLivre(eventoId));

        assertThat(assentoRepository.reservar(primeiro, eventoId, UUID.randomUUID())).isEqualTo(1);

        // Mesmo lugar, outra reserva. O WHERE exige status FREE, e ele ja nao esta.
        assertThat(assentoRepository.reservar(primeiro, eventoId, UUID.randomUUID())).isZero();
    }

    @Test
    @DisplayName("pedir tres lugares dos quais um ja saiu toma apenas dois")
    void deveContarSomenteOsQueAindaEstavamLivres() {
        UUID eventoId = casaCom(3);
        List<UUID> todos = assentoRepository
                .findByEventIdOrderBySectorNameAscRowLabelAscSeatNumberAsc(eventoId)
                .stream().map(a -> a.getId()).toList();

        AssentosDeTeste.ocupar(assentoRepository, eventoId, 1);

        // Duas linhas afetadas para tres pedidas. E essa diferenca que faz a aplicacao desfazer
        // a transacao inteira: entregar dois de tres seria uma reserva pela metade.
        assertThat(assentoRepository.reservar(todos, eventoId, UUID.randomUUID())).isEqualTo(2);
    }

    @Test
    @DisplayName("lugar ocupado sem dono e estado impossivel, e o banco recusa")
    void naoDevePermitirOcupadoSemDono() {
        UUID eventoId = casaCom(1);
        UUID assento = algumLivre(eventoId);

        // A aplicacao sempre escreve status e dono juntos. A constraint existe para o caminho
        // que alguem escrever amanha e esquecer de escrever os dois.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE event_seats SET status = 'RESERVED' WHERE id = ?", assento))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("liberar so alcanca lugar reservado, nunca vendido")
    void naoDeveLiberarLugarVendido() {
        UUID eventoId = casaCom(2);
        UUID reservaId = UUID.randomUUID();

        assentoRepository.reservar(List.of(algumLivre(eventoId)), eventoId, reservaId);
        assertThat(assentoRepository.vender(reservaId)).isEqualTo(1);

        // Um cancelamento tardio nao devolve ao pool um lugar ja pago.
        assertThat(assentoRepository.liberar(reservaId)).isZero();
        assertThat(assentoRepository.contarLivres(eventoId)).isEqualTo(1);
    }

    @Test
    @DisplayName("o estado de um lugar vendido permanece SOLD")
    void lugarVendidoDevePermanecerVendido() {
        UUID eventoId = casaCom(1);
        UUID reservaId = UUID.randomUUID();

        assentoRepository.reservar(List.of(algumLivre(eventoId)), eventoId, reservaId);
        assentoRepository.vender(reservaId);

        assertThat(assentoRepository.countByEventIdAndStatus(eventoId, SeatStatus.SOLD))
                .isEqualTo(1);
    }

    // ---------- auxiliares ----------

    private UUID casaCom(int lugares) {
        UUID eventoId = UUID.randomUUID();
        AssentosDeTeste.criarCasa(assentoRepository, eventoId, lugares, PRECO);
        return eventoId;
    }

    private UUID algumLivre(UUID eventoId) {
        return assentoRepository.escolherMaisBaratosLivres(eventoId, 1).get(0).getId();
    }

    private void inserirLugar(UUID eventoId, String setor, String fila, int numero) {
        jdbcTemplate.update("""
                INSERT INTO event_seats
                    (id, event_id, sector_name, row_label, seat_number, price, status)
                VALUES (?, ?, ?, ?, ?, ?, 'FREE')
                """, UUID.randomUUID(), eventoId, setor, fila, numero, PRECO);
    }
}
