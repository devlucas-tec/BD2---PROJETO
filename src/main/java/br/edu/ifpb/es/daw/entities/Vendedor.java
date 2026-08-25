package br.edu.ifpb.es.daw.entities;

public class Vendedor extends Usuario {

    private String razaoSocial;   
    private String cnpjCpf;      

    public Vendedor() {
    }

    public String getRazaoSocial() { return razaoSocial; }
    public void setRazaoSocial(String razaoSocial) { this.razaoSocial = razaoSocial; }

    public String getCnpjCpf() { return cnpjCpf; }
    public void setCnpjCpf(String cnpjCpf) { this.cnpjCpf = cnpjCpf; }

    @Override
    public String toString() {
        return "Vendedor{" +
                "id=" + getId() +
                ", nome='" + getNome() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", razaoSocial='" + razaoSocial + '\'' +
                ", cnpjCpf='" + cnpjCpf + '\'' +
                '}';
    }
}