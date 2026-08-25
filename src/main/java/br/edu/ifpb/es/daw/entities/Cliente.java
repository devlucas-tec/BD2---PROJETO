package br.edu.ifpb.es.daw.entities;

public class Cliente extends Usuario {

    private String telefone;

    public Cliente() {
    }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    @Override
    public String toString() {
        return "Cliente{" +
                "id=" + getId() +
                ", nome='" + getNome() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", telefone='" + telefone + '\'' +
                '}';
    }
}