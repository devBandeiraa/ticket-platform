package com.devbandeiraa.eventservice.domain;

import com.devbandeiraa.eventservice.exception.LayoutInvalidoException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Evento do catalogo.
 *
 * <p>As transicoes de estado sao metodos da propria entidade, e nao atribuicoes feitas de fora.
 * Assim a regra de "o que pode virar o que" mora em um lugar so: um servico novo que precise
 * publicar um evento nao tem como pular a verificacao, porque nao existe setter para o status.
 */
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "event_date", nullable = false)
    private Instant eventDate;

    /**
     * Capacidade da casa. <strong>Derivada</strong> dos setores — ver {@link #aplicarLayout}.
     *
     * <p>Persistida, e nao calculada na leitura, para que a listagem publica nao precise tocar em
     * {@code sectors}: uma pagina de nove eventos faria nove consultas a mais.
     */
    @Column(name = "total_tickets", nullable = false)
    private int totalTickets;

    /**
     * Menor preco entre os setores — o "a partir de" do cartao. <strong>Derivado</strong>, pelo
     * mesmo motivo de {@link #totalTickets}.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Os setores da casa, na ordem em que sao desenhados.
     *
     * <p>{@code orphanRemoval} porque um setor nao existe fora do evento: tirado da lista, ele
     * deve sumir do banco, e nao ficar orfao apontando para um evento que nao o reconhece mais.
     */
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private final List<Sector> sectors = new ArrayList<>();

    /**
     * Capa do evento, exibida no catalogo. Nula quando o evento ainda nao tem arte.
     *
     * <p>Guarda a URL, e nao o binario: servir imagem e trabalho de CDN. Ver a nota na
     * migration {@code V2}.
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * Porta de entrada do catalogo.
     *
     * <p>Obrigatoria. Um evento sem categoria so e alcancavel por busca textual, o que exclui
     * exatamente quem chega sem saber o que procura — e um catalogo existe para esse visitante.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected Event() {
    }

    private Event(String name, String description, String venue, Instant eventDate,
                  String imageUrl, EventCategory category, UUID createdBy) {
        this.name = name;
        this.description = description;
        this.venue = venue;
        this.eventDate = eventDate;
        this.imageUrl = imageUrl;
        this.category = category;
        this.createdBy = createdBy;
        this.status = EventStatus.DRAFT;
    }

    /**
     * Cria um evento em rascunho.
     *
     * <p>Nasce sempre como {@code DRAFT}, nunca publicado. Um evento aparece no catalogo por um
     * ato deliberado de quem o administra — publicar por acidente, ao salvar um cadastro pela
     * metade, e o tipo de erro que so se percebe quando alguem ja comprou.
     */
    public static Event rascunho(String name, String description, String venue, Instant eventDate,
                                 String imageUrl, EventCategory category, UUID createdBy,
                                 List<LayoutDeSetor> layout) {
        Event evento = new Event(name, description, venue, eventDate, imageUrl, category, createdBy);
        evento.aplicarLayout(layout);
        return evento;
    }

    /**
     * Substitui os setores da casa e recalcula o que deriva deles.
     *
     * <p>Substitui em vez de acrescentar: o layout e uma descricao inteira da casa, e uma
     * alteracao parcial deixaria o cliente responsavel por lembrar de reenviar os setores que nao
     * quis mudar — quem esquecesse um perderia o setor sem pedir.
     *
     * <p>A ordem de exibicao vem da ordem da lista. E a unica leitura razoavel: quem descreve a
     * casa a descreve da frente para o fundo, e exigir um campo de ordem so criaria a chance de
     * dois setores declararem o mesmo numero.
     */
    public void aplicarLayout(List<LayoutDeSetor> layout) {
        if (layout == null || layout.isEmpty()) {
            throw new LayoutInvalidoException("Um evento precisa de ao menos um setor");
        }

        List<String> nomes = layout.stream().map(LayoutDeSetor::name).toList();
        if (Set.copyOf(nomes).size() != nomes.size()) {
            // A unicidade e garantida pelo banco, mas chegar la produziria um 500 sobre uma
            // entrada que o usuario digitou — e a mensagem falaria de constraint, nao de setor.
            throw new LayoutInvalidoException("Dois setores nao podem ter o mesmo nome");
        }

        /*
          Reconcilia por NOME, em vez de limpar e recriar.

          Limpar e recriar parece mais simples e nao funciona: dentro do mesmo flush o Hibernate
          emite os INSERT antes dos DELETE, entao recriar um setor com o nome que acabou de ser
          removido viola `uk_sectors_nome` — trocar o preco da "Plateia" estourava um 500.

          Reconciliar tambem resolve de graca o caso de dois setores trocarem de nome entre si:
          com o casamento por nome, isso vira duas atualizacoes, e nao dois pares de
          delete-insert que colidiriam da mesma forma.
        */
        List<Sector> novos = new ArrayList<>();

        for (int ordem = 0; ordem < layout.size(); ordem++) {
            LayoutDeSetor descricao = layout.get(ordem);
            int posicao = ordem;

            sectors.stream()
                    .filter(setor -> setor.getName().equals(descricao.name()))
                    .findFirst()
                    .ifPresentOrElse(
                            existente -> existente.redefinir(descricao, posicao),
                            () -> novos.add(Sector.de(this, descricao, posicao)));
        }

        // Some quem saiu da planta; o orphanRemoval cuida de apaga-los do banco.
        sectors.removeIf(setor -> !nomes.contains(setor.getName()));
        sectors.addAll(novos);

        recalcularDerivados();
    }

    /**
     * Atualiza o texto, os beneficios e a faixa dos setores, sem tocar na planta.
     *
     * <p>Caminho separado de {@link #aplicarLayout} porque nao depende do estado do evento. A
     * planta so muda enquanto ha rascunho, pela razao explicada em {@link #podeAlterarLayout};
     * o que o setor diz de si e apresentacao, e um evento publicado precisa poder corrigir um
     * beneficio escrito errado sem republicar a casa inteira.
     *
     * <p>Casa por nome, e ignora em silencio quem nao encontrar. Chegar aqui com um setor
     * desconhecido significa que a planta mudou, e nesse caso quem decide e
     * {@code aplicarLayout} — nao este metodo, que criaria um setor sem capacidade declarada.
     */
    public void aplicarApresentacaoDosSetores(List<LayoutDeSetor> layout) {
        for (LayoutDeSetor descricao : layout) {
            sectors.stream()
                    .filter(setor -> setor.getName().equals(descricao.name()))
                    .findFirst()
                    .ifPresent(setor -> setor.redefinirApresentacao(descricao));
        }
    }

    /**
     * Mantem {@code totalTickets} e {@code price} coerentes com os setores.
     *
     * <p>Chamado de um unico ponto, e de proposito: dois lugares recalculando deriva em um deles
     * esquecido, e o catalogo passaria a anunciar uma capacidade que a casa nao tem.
     */
    private void recalcularDerivados() {
        this.totalTickets = sectors.stream().mapToInt(Sector::getCapacidade).sum();
        this.price = sectors.stream()
                .map(Sector::getPrice)
                .min(Comparator.naturalOrder())
                .orElseThrow();
    }

    /**
     * O layout so muda enquanto o evento e rascunho.
     *
     * <p>Publicado, ele pode ter reservas — e o booking-service ja hidratou a capacidade do lado
     * dele. Mexer nos setores aqui mudaria a casa por baixo de quem ja comprou: um lugar vendido
     * poderia deixar de existir, ou a capacidade cair abaixo do que ja foi vendido. Nome, data e
     * descricao seguem editaveis; a planta da casa, nao.
     */
    public boolean podeAlterarLayout() {
        return status == EventStatus.DRAFT;
    }

    /** Um evento cancelado nao volta atras: seus dados ficam congelados. */
    public boolean podeSerAlterado() {
        return status != EventStatus.CANCELLED;
    }

    public boolean estaPublicado() {
        return status == EventStatus.PUBLISHED;
    }

    /** Dados de apresentacao. O layout tem caminho proprio — ver {@link #aplicarLayout}. */
    public void alterarDados(String name, String description, String venue, Instant eventDate,
                             String imageUrl, EventCategory category) {
        this.name = name;
        this.description = description;
        this.venue = venue;
        this.eventDate = eventDate;
        this.imageUrl = imageUrl;
        this.category = category;
    }

    public void publicar() {
        this.status = EventStatus.PUBLISHED;
    }

    public void cancelar() {
        this.status = EventStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getVenue() {
        return venue;
    }

    public Instant getEventDate() {
        return eventDate;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public EventCategory getCategory() {
        return category;
    }

    /**
     * Somente leitura: o layout se altera por {@link #aplicarLayout}, nunca pela lista.
     *
     * <p>Ordenado aqui, e nao so pelo {@code @OrderBy}: a anotacao vale para o que o banco
     * devolve, e nao reordena a colecao ja em memoria depois de uma reconciliacao. Sem isto, a
     * resposta da mesma requisicao que altera o layout sairia numa ordem, e a da leitura
     * seguinte, em outra.
     */
    public List<Sector> getSectors() {
        return sectors.stream()
                .sorted(Comparator.comparingInt(Sector::getDisplayOrder))
                .toList();
    }

    public EventStatus getStatus() {
        return status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Identidade por id: duas instancias carregadas em sessoes diferentes representam o mesmo
     * evento. Enquanto o id for nulo (entidade ainda nao persistida), so a identidade da propria
     * referencia vale.
     */
    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof Event evento)) {
            return false;
        }
        return id != null && id.equals(evento.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
