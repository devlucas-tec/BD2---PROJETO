package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.CategoriaDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Categoria;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class CategoriaDAOImpl extends AbstractDAOImpl<Categoria> implements CategoriaDAO {

    private static final RowMapper<Categoria> CATEGORIA_MAPPER = rs -> {
        Categoria c = new Categoria();
        c.setId(rs.getLong("id"));
        c.setNome(rs.getString("nome"));
        c.setDescricao(rs.getString("descricao"));
        return c;
    };

    private static final String INSERT_SQL = "INSERT INTO categoria (nome, descricao) VALUES (?, ?) RETURNING id";
    private static final String FIND_BY_ID_SQL = "SELECT id, nome, descricao FROM categoria WHERE id = ?";
    private static final String FIND_ALL_SQL = "SELECT id, nome, descricao FROM categoria";
    private static final String UPDATE_SQL = "UPDATE categoria SET nome = ?, descricao = ? WHERE id = ?";
    private static final String DELETE_SQL = "DELETE FROM categoria WHERE id = ?";
    private static final String DELETE_ALL_SQL = "DELETE FROM categoria";

    @Override
    public void save(Categoria categoria) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, categoria.getNome());
                stmt.setString(2, categoria.getDescricao());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        categoria.setId(rs.getLong("id"));
                    }
                }
            }
        });
    }

    @Override
    public Categoria findById(Long id) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_SQL)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return CATEGORIA_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Categoria> findAll() {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Categoria> categorias = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(FIND_ALL_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        categorias.add(CATEGORIA_MAPPER.mapRow(rs));
                    }
                }
            }
            return categorias;
        });
    }

    @Override
    public void update(Categoria categoria) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setString(1, categoria.getNome());
                stmt.setString(2, categoria.getDescricao());
                stmt.setLong(3, categoria.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Categoria categoria) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setLong(1, categoria.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void deleteAll() {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_ALL_SQL)) {
                stmt.executeUpdate();
            }
        });
    }
}