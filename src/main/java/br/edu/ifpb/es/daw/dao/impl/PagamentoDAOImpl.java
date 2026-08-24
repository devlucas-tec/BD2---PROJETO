package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.PagamentoDAO;
import br.edu.ifpb.es.daw.entities.Pagamento;

import java.util.List;

public class PagamentoDAOImpl extends AbstractDAOImpl<Pagamento> implements PagamentoDAO {

    @Override
    public void save(Pagamento entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public Pagamento findById(Long id) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public List<Pagamento> findAll() {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void update(Pagamento entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void delete(Pagamento entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void deleteAll() {
        throw new UnsupportedOperationException("Ainda não implementado");
    }
}