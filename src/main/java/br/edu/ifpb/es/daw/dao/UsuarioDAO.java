package br.edu.ifpb.es.daw.dao;

import br.edu.ifpb.es.daw.entities.Usuario;

public interface UsuarioDAO extends DAO<Usuario> {

    Usuario findByEmail(String email);

    boolean existsByEmail(String email);
}