package br.edu.ifpb.es.daw.entities;

import java.time.LocalDateTime;
import java.util.Objects;

public class Usuario {

    private Long id;
    private String nome;
    private String email;
    private String senhaHash;      
    private String role;           
    private boolean ativo;
    private LocalDateTime dataCadastro;
    private LocalDateTime dataAtualizacao;
    private String dtype;          

    public Usuario() {
    }


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }

    public LocalDateTime getDataCadastro() { return dataCadastro; }
    public void setDataCadastro(LocalDateTime dataCadastro) { this.dataCadastro = dataCadastro; }

    public LocalDateTime getDataAtualizacao() { return dataAtualizacao; }
    public void setDataAtualizacao(LocalDateTime dataAtualizacao) { this.dataAtualizacao = dataAtualizacao; }

    public String getDtype() { return dtype; }
    public void setDtype(String dtype) { this.dtype = dtype; }

    public void onCreate() {
        this.dataCadastro = LocalDateTime.now();
        this.dataAtualizacao = LocalDateTime.now();
    }

    public void onUpdate() {
        this.dataAtualizacao = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return Objects.equals(email, usuario.email);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(email);
    }

    @Override
    public String toString() {
        return "Usuario{" +
                "id=" + id +
                ", nome='" + nome + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", ativo=" + ativo +
                '}';
    }
}