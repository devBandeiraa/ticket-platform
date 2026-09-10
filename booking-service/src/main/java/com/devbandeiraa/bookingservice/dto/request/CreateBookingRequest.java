package com.devbandeiraa.bookingservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Pedido de reserva.
 *
 * <p>Nao ha campo de preco nem de usuario. O preco vem dos lugares tomados, e o usuario vem do
 * token: aceitar qualquer um dos dois no corpo permitiria reservar em nome de outra pessoa ou
 * definir o proprio preco.
 *
 * <h2>Duas formas de escolher</h2>
 *
 * <p>Com {@code seatIds}, o cliente diz exatamente quais lugares quer — e o pedido e tudo ou
 * nada. Sem eles, {@code quantity} pede os N livres mais baratos, o classico "melhor
 * disponivel".
 *
 * <p>As duas convergem no mesmo {@code UPDATE} condicional que impede vender o mesmo lugar
 * duas vezes: sao duas formas de <em>escolher</em>, e nao duas logicas de correcao.
 *
 * @param eventId  evento a reservar
 * @param seatIds  lugares escolhidos; quando ausente, o servidor escolhe
 * @param quantity quantos lugares, quando a escolha e do servidor
 */
public record CreateBookingRequest(

        @NotNull(message = "eventId e obrigatorio")
        UUID eventId,

        // Teto por reserva, aqui sim: com escolha explicita, uma lista enorme viraria um IN com
        // milhares de ids e um lock sobre metade da casa. Dez e o mesmo limite que a tela
        // oferece.
        @Size(max = 10, message = "no maximo 10 lugares por reserva")
        List<UUID> seatIds,

        // Vale apenas quando seatIds nao vem. Um pedido absurdo simplesmente nao cabe e recebe
        // 409 SOLD_OUT.
        @Min(value = 1, message = "quantity precisa ser no minimo 1")
        int quantity) {

    /** O cliente escolheu os lugares, em vez de deixar o servidor escolher. */
    public boolean temEscolhaExplicita() {
        return seatIds != null && !seatIds.isEmpty();
    }

    /**
     * Quantos lugares o pedido representa, venha a escolha de onde vier.
     *
     * <p>Com lugares escolhidos, a quantidade e o tamanho da lista — e um {@code quantity}
     * divergente enviado junto e ignorado, e nao recusado: os ids sao a intencao inequivoca, e
     * recusar por causa de um campo redundante seria rigor sem ganho.
     */
    public int quantidadePedida() {
        return temEscolhaExplicita() ? seatIds.size() : quantity;
    }

    /** Remove ids repetidos: pedir o mesmo lugar duas vezes e uma so intencao. */
    public List<UUID> assentosDistintos() {
        return seatIds == null ? List.of() : seatIds.stream().distinct().toList();
    }
}
