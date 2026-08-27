package br.edu.ifpb.es.daw.entities;

import java.time.LocalDateTime;
import java.util.Objects;


public class Avaliacao {


    private Long id;


    private Integer nota;


    private String comentario;


    private LocalDateTime dataAvaliacao;

    /**
     * Autor da avaliação (FK para cliente.id).
     *
     * É esta coluna que a policy avaliacao_insert compara com app.usuario_id
     * para impedir que um cliente assine avaliação em nome de outro.
     */
    private Long idCliente;

    /** Produto avaliado (FK para produto.id_produto). */
    private Long idProduto;


    public void onCreate() {
        this.dataAvaliacao = LocalDateTime.now();
    }

    public Avaliacao() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getNota() {
        return nota;
    }

    public void setNota(Integer nota) {
        this.nota = nota;
    }

    public String getComentario() {
        return comentario;
    }

    public void setComentario(String comentario) {
        this.comentario = comentario;
    }

    public LocalDateTime getDataAvaliacao() {
        return dataAvaliacao;
    }

    public void setDataAvaliacao(LocalDateTime dataAvaliacao) {
        this.dataAvaliacao = dataAvaliacao;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(Long idCliente) {
        this.idCliente = idCliente;
    }

    public Long getIdProduto() {
        return idProduto;
    }

    public void setIdProduto(Long idProduto) {
        this.idProduto = idProduto;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Avaliacao avaliacao = (Avaliacao) o;
        return Objects.equals(id, avaliacao.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Avaliacao{" +
                "id=" + id +
                ", nota=" + nota +
                ", comentario='" + comentario + '\'' +
                ", dataAvaliacao=" + dataAvaliacao +
                ", idCliente=" + idCliente +
                ", idProduto=" + idProduto +
                '}';
    }

}
