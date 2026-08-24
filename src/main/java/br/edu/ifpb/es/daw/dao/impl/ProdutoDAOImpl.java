package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.ProdutoDAO;
import br.edu.ifpb.es.daw.entities.Produto;

import java.util.List;

public class ProdutoDAOImpl extends AbstractDAOImpl<Produto> implements ProdutoDAO {

    @Override
    public void save(Produto entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public Produto findById(Long id) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public List<Produto> findAll() {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void update(Produto entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void delete(Produto entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void deleteAll() {
        throw new UnsupportedOperationException("Ainda não implementado");
    }
}