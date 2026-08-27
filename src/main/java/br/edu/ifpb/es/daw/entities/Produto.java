package br.edu.ifpb.es.daw.entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public class Produto {

    private Long id;

    private String nome;

    private String descricao;

    private Integer estoque;

    private BigDecimal preco;

    private Long idVendedor;

    private Long idCategoria;

    private LocalDateTime dataCadastro;

    private LocalDateTime dataAtualizacao;

    /**
     * Categoria dona do produto, materializada pelo RowMapper (issue #8).
     *
     * Sempre vem preenchida nas consultas do ProdutoDAO: categoria é vitrine
     * pública no RLS, então o JOIN nunca perde linha.
     */
    private Categoria categoria;

    /**
     * Vendedor dono do produto, carregado SOB DEMANDA (issue #8).
     *
     * Fica null nas consultas do ProdutoDAO. Para materializá-lo, chame
     * ProdutoDAO.carregarVendedor(produto) — e mesmo assim ele continua null
     * quando o RLS de vendedor/usuario nega a leitura para o contexto atual
     * (só o próprio vendedor ou um ADMIN enxergam esses dados).
     *
     * O porquê dessa assimetria está documentado em ProdutoDAOImpl.
     */
    private Vendedor vendedor;

    public void onCreate() {
        this.dataCadastro = LocalDateTime.now();
        this.dataAtualizacao = LocalDateTime.now();
    }

    public void onUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
    }

    public Produto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public Integer getEstoque() {
        return estoque;
    }

    public void setEstoque(Integer estoque) {
        this.estoque = estoque;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public Long getIdVendedor() {
        return idVendedor;
    }

    public void setIdVendedor(Long idVendedor) {
        this.idVendedor = idVendedor;
    }

    public Long getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(Long idCategoria) {
        this.idCategoria = idCategoria;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    /**
     * A FK (idCategoria) continua sendo o que o DAO grava. Manter as duas
     * pontas em sincronia aqui evita que um save() feito a partir do objeto
     * associado escreva id_categoria = null.
     */
    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
        if (categoria != null && categoria.getId() != null) {
            this.idCategoria = categoria.getId();
        }
    }

    public Vendedor getVendedor() {
        return vendedor;
    }

    /** Mesmo contrato de setCategoria: mantém a FK idVendedor em sincronia. */
    public void setVendedor(Vendedor vendedor) {
        this.vendedor = vendedor;
        if (vendedor != null && vendedor.getId() != null) {
            this.idVendedor = vendedor.getId();
        }
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public void setDataCadastro(LocalDateTime dataCadastro) {
        this.dataCadastro = dataCadastro;
    }

    public LocalDateTime getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(LocalDateTime dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Produto produto = (Produto) o;
        return Objects.equals(id, produto.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Produto{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", preco=" + preco +
                ", estoque=" + estoque +
                ", idVendedor=" + idVendedor +
                ", idCategoria=" + idCategoria +
                ", categoria=" + (categoria != null ? categoria.getNome() : "<nao carregada>") +
                ", vendedor=" + (vendedor != null ? vendedor.getRazaoSocial() : "<nao carregado>") +
                '}';
    }
}
