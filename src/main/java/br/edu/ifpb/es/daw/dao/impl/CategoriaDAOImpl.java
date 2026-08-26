package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.CategoriaDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Categoria;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CategoriaDAOImpl extends AbstractDAOImpl<Categoria> implements CategoriaDAO {

    private static final RowMapper<Categoria> CATEGORIA_MAPPER = rs -> mapRow(rs, "");

    /**
     * Mapeia uma categoria a partir de colunas eventualmente prefixadas.
     *
     * O ProdutoDAOImpl traz a categoria junto no mesmo SELECT e precisa
     * apelidar as colunas (categoria_id, categoria_nome, ...) para não
     * colidir com as de produto. Centralizar o mapeamento aqui evita que os
     * dois DAOs escrevam a mesma lista de colunas.
     *
     * @param prefixo prefixo dos aliases ("" para as colunas cruas de categoria)
     */
    static Categoria mapRow(ResultSet rs, String prefixo) throws SQLException {
        Categoria c = new Categoria();
        c.setId(rs.getLong(prefixo + "id"));
        c.setNome(rs.getString(prefixo + "nome"));
        c.setDescricao(rs.getString(prefixo + "descricao"));
        return c;
    }

    private static final String INSERT_SQL = "INSERT INTO categoria (nome, descricao) VALUES (?, ?) RETURNING id";
    private static final String FIND_BY_ID_SQL = "SELECT id, nome, descricao FROM categoria WHERE id = ?";
    private static final String FIND_BY_NOME_SQL = "SELECT id, nome, descricao FROM categoria WHERE nome = ?";
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
    public Categoria findByNome(String nome) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_NOME_SQL)) {
                stmt.setString(1, nome);
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
