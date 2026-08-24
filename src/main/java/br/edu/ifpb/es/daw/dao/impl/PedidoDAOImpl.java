package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.PedidoDAO;
import br.edu.ifpb.es.daw.entities.Pedido;

import java.util.List;

public class PedidoDAOImpl extends AbstractDAOImpl<Pedido> implements PedidoDAO {

    @Override
    public void save(Pedido entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public Pedido findById(Long id) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public List<Pedido> findAll() {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void update(Pedido entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void delete(Pedido entity) {
        throw new UnsupportedOperationException("Ainda não implementado");
    }

    @Override
    public void deleteAll() {
        throw new UnsupportedOperationException("Ainda não implementado");
    }
}