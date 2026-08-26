package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.ProdutoDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Produto;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ProdutoDAOImpl extends AbstractDAOImpl<Produto> implements ProdutoDAO {

    private static final RowMapper<Produto> PRODUTO_MAPPER = rs -> {
        Produto p = new Produto();
        p.setId(rs.getLong("id_produto"));
        p.setNome(rs.getString("nome"));
        p.setDescricao(rs.getString("descricao"));
        p.setEstoque(rs.getInt("estoque"));
        p.setPreco(rs.getBigDecimal("preco"));
        p.setIdVendedor(rs.getLong("id_vendedor"));
        p.setIdCategoria(rs.getLong("id_categoria"));
        Timestamp tsCadastro = rs.getTimestamp("data_cadastro");
        if (tsCadastro != null) {
            p.setDataCadastro(tsCadastro.toLocalDateTime());
        }
        Timestamp tsAtualizacao = rs.getTimestamp("data_atualizacao");
        if (tsAtualizacao != null) {
            p.setDataAtualizacao(tsAtualizacao.toLocalDateTime());
        }
        return p;
    };

    private static final String INSERT_SQL = """
            INSERT INTO produto (nome, descricao, estoque, preco, id_vendedor, id_categoria, data_cadastro, data_atualizacao)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id_produto
            """;

    private static final String FIND_BY_ID_SQL = """
            SELECT id_produto, nome, descricao, estoque, preco, id_vendedor, id_categoria, data_cadastro, data_atualizacao
            FROM produto
            WHERE id_produto = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT id_produto, nome, descricao, estoque, preco, id_vendedor, id_categoria, data_cadastro, data_atualizacao
            FROM produto
            """;

    private static final String UPDATE_SQL = """
            UPDATE produto
            SET nome = ?, descricao = ?, estoque = ?, preco = ?, id_vendedor = ?, id_categoria = ?, data_atualizacao = ?
            WHERE id_produto = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM produto WHERE id_produto = ?";
    private static final String DELETE_ALL_SQL = "DELETE FROM produto";

    @Override
    public void save(Produto produto) {
        produto.onCreate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, produto.getNome());
                stmt.setString(2, produto.getDescricao());
                stmt.setInt(3, produto.getEstoque() != null ? produto.getEstoque() : 0);
                stmt.setBigDecimal(4, produto.getPreco());
                stmt.setLong(5, produto.getIdVendedor());
                stmt.setLong(6, produto.getIdCategoria());
                stmt.setTimestamp(7, Timestamp.valueOf(produto.getDataCadastro()));
                stmt.setTimestamp(8, Timestamp.valueOf(produto.getDataAtualizacao()));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        produto.setId(rs.getLong("id_produto"));
                    }
                }
            }
        });
    }

    @Override
    public Produto findById(Long id) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_SQL)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return PRODUTO_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Produto> findAll() {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Produto> produtos = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(FIND_ALL_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        produtos.add(PRODUTO_MAPPER.mapRow(rs));
                    }
                }
            }
            return produtos;
        });
    }

    @Override
    public void update(Produto produto) {
        produto.onUpdate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setString(1, produto.getNome());
                stmt.setString(2, produto.getDescricao());
                stmt.setInt(3, produto.getEstoque() != null ? produto.getEstoque() : 0);
                stmt.setBigDecimal(4, produto.getPreco());
                stmt.setLong(5, produto.getIdVendedor());
                stmt.setLong(6, produto.getIdCategoria());
                stmt.setTimestamp(7, Timestamp.valueOf(produto.getDataAtualizacao()));
                stmt.setLong(8, produto.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Produto produto) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setLong(1, produto.getId());
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