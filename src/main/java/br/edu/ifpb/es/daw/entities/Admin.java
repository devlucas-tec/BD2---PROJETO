package br.edu.ifpb.es.daw.entities;

public class Admin extends Usuario {

    public Admin() {
    }

    @Override
    public String toString() {
        return "Admin{" +
                "id=" + getId() +
                ", nome='" + getNome() + '\'' +
                ", email='" + getEmail() + '\'' +
                '}';
    }
}