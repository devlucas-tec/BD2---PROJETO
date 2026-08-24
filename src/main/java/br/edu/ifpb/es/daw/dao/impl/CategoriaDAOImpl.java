package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.CategoriaDAO;
import br.edu.ifpb.es.daw.entities.Categoria;

import java.util.List;

public class CategoriaDAOImpl extends AbstractDAOImpl<Categoria> implements CategoriaDAO {

    @Override
    public void save(Categoria entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public Categoria findById(Long id) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public List<Categoria> findAll() {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void update(Categoria entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void delete(Categoria entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void deleteAll() {
        throw new UnsupportedOperationException("Ainda não implementado");
    }
}