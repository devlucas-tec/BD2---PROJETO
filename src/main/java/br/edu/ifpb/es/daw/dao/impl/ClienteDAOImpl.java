package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.ClienteDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Cliente;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ClienteDAOImpl extends AbstractDAOImpl<Cliente> implements ClienteDAO {

    private static final RowMapper<Cliente> CLIENTE_MAPPER = rs -> {
        Cliente c = new Cliente();
        c.setId(rs.getLong("id"));
        c.setNome(rs.getString("nome"));
        c.setEmail(rs.getString("email"));
        c.setSenhaHash(rs.getString("senha_hash"));
        c.setRole(rs.getString("role"));
        c.setAtivo(rs.getBoolean("ativo"));
        c.setDataCadastro(rs.getTimestamp("data_cadastro").toLocalDateTime());
        c.setDataAtualizacao(rs.getTimestamp("data_atualizacao").toLocalDateTime());
        c.setDtype(rs.getString("dtype"));
        c.setTelefone(rs.getString("telefone"));
        return c;
    };

    private static final String INSERT_USUARIO_SQL = """
            INSERT INTO usuario (nome, email, senha_hash, role, ativo, data_cadastro, data_atualizacao, dtype)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String INSERT_CLIENTE_SQL = "INSERT INTO cliente (id, telefone) VALUES (?, ?)";

    private static final String FIND_BY_ID_SQL = """
            SELECT u.id, u.nome, u.email, u.senha_hash, u.role, u.ativo,
                   u.data_cadastro, u.data_atualizacao, u.dtype, c.telefone
            FROM usuario u JOIN cliente c ON u.id = c.id
            WHERE u.id = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT u.id, u.nome, u.email, u.senha_hash, u.role, u.ativo,
                   u.data_cadastro, u.data_atualizacao, u.dtype, c.telefone
            FROM usuario u JOIN cliente c ON u.id = c.id
            """;

    private static final String UPDATE_USUARIO_SQL = """
            UPDATE usuario SET nome = ?, email = ?, senha_hash = ?, role = ?, ativo = ?,
                data_atualizacao = ?, dtype = ? WHERE id = ?
            """;

    private static final String UPDATE_CLIENTE_SQL = "UPDATE cliente SET telefone = ? WHERE id = ?";

    private static final String DELETE_CLIENTE_SQL = "DELETE FROM cliente WHERE id = ?";

    private static final String DELETE_USUARIO_SQL = "DELETE FROM usuario WHERE id = ?";

    @Override
    public void save(Cliente cliente) {
        cliente.onCreate();
        cliente.setRole("CLIENTE");
        cliente.setAtivo(true);
        cliente.setDtype("CLIENTE");

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_USUARIO_SQL)) {
                stmt.setString(1, cliente.getNome());
                stmt.setString(2, cliente.getEmail());
                stmt.setString(3, cliente.getSenhaHash());
                stmt.setString(4, cliente.getRole());
                stmt.setBoolean(5, cliente.isAtivo());
                stmt.setTimestamp(6, Timestamp.valueOf(cliente.getDataCadastro()));
                stmt.setTimestamp(7, Timestamp.valueOf(cliente.getDataAtualizacao()));
                stmt.setString(8, cliente.getDtype());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cliente.setId(rs.getLong("id"));
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(INSERT_CLIENTE_SQL)) {
                stmt.setLong(1, cliente.getId());
                stmt.setString(2, cliente.getTelefone());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public Cliente findById(Long id) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_SQL)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return CLIENTE_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Cliente> findAll() {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Cliente> clientes = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(FIND_ALL_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        clientes.add(CLIENTE_MAPPER.mapRow(rs));
                    }
                }
            }
            return clientes;
        });
    }

    @Override
    public void update(Cliente cliente) {
        cliente.onUpdate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_USUARIO_SQL)) {
                stmt.setString(1, cliente.getNome());
                stmt.setString(2, cliente.getEmail());
                stmt.setString(3, cliente.getSenhaHash());
                stmt.setString(4, cliente.getRole());
                stmt.setBoolean(5, cliente.isAtivo());
                stmt.setTimestamp(6, Timestamp.valueOf(cliente.getDataAtualizacao()));
                stmt.setString(7, cliente.getDtype());
                stmt.setLong(8, cliente.getId());
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_CLIENTE_SQL)) {
                stmt.setString(1, cliente.getTelefone());
                stmt.setLong(2, cliente.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Cliente cliente) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_CLIENTE_SQL)) {
                stmt.setLong(1, cliente.getId());
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_USUARIO_SQL)) {
                stmt.setLong(1, cliente.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void deleteAll() {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM cliente")) { stmt.executeUpdate(); }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM usuario WHERE dtype = 'CLIENTE'")) { stmt.executeUpdate(); }
        });
    }
}