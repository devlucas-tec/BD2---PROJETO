package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.ClienteDAO;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClienteDAOImpl extends AbstractDAOImpl<Cliente> implements ClienteDAO {

    @Override
    public void save(Cliente cliente) {
        String sql = """
            INSERT INTO cliente (nome, email, senha_hash, telefone, data_cadastro, data_atualizacao)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        cliente.onCreate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, cliente.getNome());
                stmt.setString(2, cliente.getEmail());
                stmt.setString(3, cliente.getSenha());
                stmt.setString(4, cliente.getTelefone());
                stmt.setObject(5, cliente.getDataCadastro());
                stmt.setObject(6, cliente.getDataAtualizacao());

                int affectedRows = stmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new RuntimeException(
                            "Falha ao salvar cliente: nenhuma linha afetada.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        cliente.setId(generatedKeys.getLong(1));
                    } else {
                        throw new RuntimeException(
                                "Falha ao salvar cliente: ID não gerado.");
                    }
                }
            }
        });
    }

    @Override
    public Cliente findById(Long id) {
        String sql = "SELECT * FROM cliente WHERE id_cliente = ?";

        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToCliente(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Cliente> findAll() {
        String sql = "SELECT * FROM cliente ORDER BY id_cliente";

        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Cliente> clientes = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    clientes.add(mapResultSetToCliente(rs));
                }
            }
            return clientes;
        });
    }

    @Override
    public void update(Cliente cliente) {
        String sql = """
            UPDATE cliente
            SET nome = ?, email = ?, senha_hash = ?, telefone = ?, data_atualizacao = ?
            WHERE id_cliente = ?
            """;

        cliente.onUpdate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, cliente.getNome());
                stmt.setString(2, cliente.getEmail());
                stmt.setString(3, cliente.getSenha());
                stmt.setString(4, cliente.getTelefone());
                stmt.setObject(5, cliente.getDataAtualizacao());
                stmt.setLong(6, cliente.getId());

                int affectedRows = stmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new RuntimeException(
                            "Falha ao atualizar cliente: nenhuma linha afetada. ID: "
                                    + cliente.getId());
                }
            }
        });
    }

    @Override
    public void delete(Cliente cliente) {
        String sql = "DELETE FROM cliente WHERE id_cliente = ?";

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, cliente.getId());

                int affectedRows = stmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new RuntimeException(
                            "Falha ao deletar cliente: nenhuma linha afetada. ID: "
                                    + cliente.getId());
                }
            }
        });
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM cliente";

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.executeUpdate();
            }
        });
    }

    private Cliente mapResultSetToCliente(ResultSet rs) throws SQLException {
        Cliente cliente = new Cliente();
        cliente.setId(rs.getLong("id_cliente"));
        cliente.setNome(rs.getString("nome"));
        cliente.setEmail(rs.getString("email"));
        cliente.setSenha(rs.getString("senha_hash"));
        cliente.setTelefone(rs.getString("telefone"));

        Timestamp tsCadastro = rs.getTimestamp("data_cadastro");
        if (tsCadastro != null) {
            cliente.setDataCadastro(tsCadastro.toLocalDateTime());
        }

        Timestamp tsAtualizacao = rs.getTimestamp("data_atualizacao");
        if (tsAtualizacao != null) {
            cliente.setDataAtualizacao(tsAtualizacao.toLocalDateTime());
        }

        return cliente;
    }
}