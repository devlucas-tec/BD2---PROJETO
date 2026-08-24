package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.ClienteDAO;
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

        // Preenche as datas automaticamente (antes era @PrePersist)
        cliente.onCreate();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, cliente.getNome());
            stmt.setString(2, cliente.getEmail());
            stmt.setString(3, cliente.getSenha());
            stmt.setString(4, cliente.getTelefone());
            stmt.setObject(5, cliente.getDataCadastro());
            stmt.setObject(6, cliente.getDataAtualizacao());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new RuntimeException("Falha ao salvar cliente: nenhuma linha afetada.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    cliente.setId(generatedKeys.getLong(1));
                } else {
                    throw new RuntimeException("Falha ao salvar cliente: ID não gerado.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar cliente", e);
        }
    }

    @Override
    public Cliente findById(Long id) {
        String sql = "SELECT * FROM cliente WHERE id_cliente = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCliente(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar cliente por ID: " + id, e);
        }

        return null;
    }

    @Override
    public List<Cliente> findAll() {
        String sql = "SELECT * FROM cliente ORDER BY id_cliente";
        List<Cliente> clientes = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                clientes.add(mapResultSetToCliente(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar todos os clientes", e);
        }

        return clientes;
    }

    @Override
    public void update(Cliente cliente) {
        String sql = """
            UPDATE cliente
            SET nome = ?, email = ?, senha_hash = ?, telefone = ?, data_atualizacao = ?
            WHERE id_cliente = ?
            """;

        // Atualiza a data automaticamente (antes era @PreUpdate)
        cliente.onUpdate();

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, cliente.getNome());
            stmt.setString(2, cliente.getEmail());
            stmt.setString(3, cliente.getSenha());
            stmt.setString(4, cliente.getTelefone());
            stmt.setObject(5, cliente.getDataAtualizacao());
            stmt.setLong(6, cliente.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new RuntimeException(
                        "Falha ao atualizar cliente: nenhuma linha afetada. ID: " + cliente.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar cliente", e);
        }
    }

    @Override
    public void delete(Cliente cliente) {
        String sql = "DELETE FROM cliente WHERE id_cliente = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, cliente.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new RuntimeException(
                        "Falha ao deletar cliente: nenhuma linha afetada. ID: " + cliente.getId());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao deletar cliente", e);
        }
    }

    @Override
    public void deleteAll() {
        String sql = "DELETE FROM cliente";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao deletar todos os clientes", e);
        }
    }

    /**
     * Mapeia uma linha do ResultSet para um objeto Cliente.
     * Este método é o coração do JDBC manual — é onde você "traduz"
     * colunas do banco para campos do objeto Java.
     */
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