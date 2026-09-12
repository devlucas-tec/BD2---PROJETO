package br.edu.ifpb.es.daw.entities;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * item_carrinho não tem coluna "id": a PK é composta por (carrinho_id,
 * produto_id) — ver 07_ddl_item_carrinho.sql. Antes da migração para JDBC
 * (issue #13) essa chave era um @EmbeddedId; sem JPA, ela é modelada aqui
 * como duas FKs simples (carrinhoId, produtoId) e o DAO trata o par
 * manualmente em cada SQL (ON CONFLICT / WHERE carrinho_id = ? AND
 * produto_id = ?).
 */
public class ItemCarrinho {

    private Long carrinhoId;

    private Long produtoId;

    private BigDecimal precoUnitario;

    private Integer quantidade;

    public ItemCarrinho() {
    }

    public ItemCarrinho(Long carrinhoId, Long produtoId, Integer quantidade, BigDecimal precoUnitario) {
        this.carrinhoId = carrinhoId;
        this.produtoId = produtoId;
        this.quantidade = quantidade;
        this.precoUnitario = precoUnitario;
    }

    public Long getCarrinhoId() {
        return carrinhoId;
    }

    public void setCarrinhoId(Long carrinhoId) {
        this.carrinhoId = carrinhoId;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public void setProdutoId(Long produtoId) {
        this.produtoId = produtoId;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public void setQuantidade(Integer quantidade) {
        this.quantidade = quantidade;
    }

    public BigDecimal getPrecoUnitario() { return precoUnitario; }

    public void setPrecoUnitario(BigDecimal precoUnitario) { this.precoUnitario = precoUnitario; }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ItemCarrinho that = (ItemCarrinho) o;
        // Identidade = chave primária composta, não quantidade/preco.
        return Objects.equals(carrinhoId, that.carrinhoId) && Objects.equals(produtoId, that.produtoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(carrinhoId, produtoId);
    }

    @Override
    public String toString() {
        return "ItemCarrinho{" +
                "carrinhoId=" + carrinhoId +
                ", produtoId=" + produtoId +
                ", precoUnitario=" + precoUnitario +
                ", quantidade=" + quantidade +
                '}';
    }
}
