package br.edu.ifpb.es.daw.entities;

import java.util.Objects;

public class Vendedor {

    private Long id;

    private String razaoSocial;

    private String cnpjCpf;

    public Vendedor() {

    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public void setRazaoSocial(String razaoSocial) {
        this.razaoSocial = razaoSocial;
    }

    public String getCnpjCpf() {
        return cnpjCpf;
    }

    public void setCnpjCpf(String cnpjCpf) {
        this.cnpjCpf = cnpjCpf;
    }



    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Vendedor vendedor = (Vendedor) obj;
        return Objects.equals(cnpjCpf, vendedor.cnpjCpf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cnpjCpf);
    }

    @Override
    public String toString() {
        return "Vendedor{" +
                "cnpjCpf='" + cnpjCpf + '\'' +
                '}';
    }

}
