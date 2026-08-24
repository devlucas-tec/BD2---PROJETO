package br.edu.ifpb.es.daw.entities;

import java.time.LocalDateTime;
import java.util.Objects;

public class Devolucao {

    private Long id;

    private LocalDateTime dataDevolucao;

    private String motivo;

    private StatusDevolucao status = StatusDevolucao.APROVADA;

    public Devolucao() {
    }

    public void onCreate() {
        this.dataDevolucao = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDataDevolucao() {
        return dataDevolucao;
    }

    public void setDataDevolucao(LocalDateTime dataDevolucao) {
        this.dataDevolucao = dataDevolucao;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public StatusDevolucao getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = StatusDevolucao.valueOf(status);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Devolucao devolucao = (Devolucao) o;
        return Objects.equals(id, devolucao.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Devolucao{" +
                "id=" + id +
                ", status='" + status + '\'' +
                '}';
    }

}
