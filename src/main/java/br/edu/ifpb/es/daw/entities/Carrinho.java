package br.edu.ifpb.es.daw.entities;

import java.time.LocalDate;
import java.util.Objects;

public class Carrinho {

    private Long id;

    private LocalDate dataCriacao;

    private LocalDate dataAtualizacao;

    /**
     * FK para usuario.id (o "dono" do carrinho). NOT NULL + UNIQUE no DDL:
     * cada cliente tem no máximo um carrinho (ver 06_ddl_carrinho.sql).
     */
    private Long idCliente;

    public void onCreate() {
        this.dataCriacao = LocalDate.now();
        this.dataAtualizacao = LocalDate.now();
    }

    public void ondUpdate() {
        this.dataAtualizacao = LocalDate.now();
    }

    public Carrinho() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(LocalDate dataCriacao) {
        this.dataCriacao = dataCriacao;
    }

    public LocalDate getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao(LocalDate dataAtualizacao) {
        this.dataAtualizacao = dataAtualizacao;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(Long idCliente) {
        this.idCliente = idCliente;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Carrinho carrinho = (Carrinho) o;
        return Objects.equals(id, carrinho.id) && Objects.equals(dataCriacao, carrinho.dataCriacao)
                && Objects.equals(dataAtualizacao, carrinho.dataAtualizacao) && Objects.equals(idCliente, carrinho.idCliente);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dataCriacao, dataAtualizacao, idCliente);
    }

    @Override
    public String toString() {
        return "Carrinho{" +
                "id=" + id +
                ", dataCriacao=" + dataCriacao +
                ", dataAtualizacao=" + dataAtualizacao +
                ", idCliente=" + idCliente +
                '}';
    }
}
