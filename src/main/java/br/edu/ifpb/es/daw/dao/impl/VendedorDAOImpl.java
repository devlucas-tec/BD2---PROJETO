package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.dao.VendedorDAO;
import br.edu.ifpb.es.daw.entities.Vendedor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class VendedorDAOImpl extends AbstractDAOImpl<Vendedor> implements VendedorDAO {

    private static final RowMapper<Vendedor> VENDEDOR_MAPPER = rs -> {
        Vendedor v = new Vendedor();
        v.setId(rs.getLong("id"));
        v.setNome(rs.getString("nome"));
        v.setEmail(rs.getString("email"));
        v.setSenhaHash(rs.getString("senha_hash"));
        v.setRole(rs.getString("role"));
        v.setAtivo(rs.getBoolean("ativo"));
        v.setDataCadastro(rs.getTimestamp("data_cadastro").toLocalDateTime());
        v.setDataAtualizacao(rs.getTimestamp("data_atualizacao").toLocalDateTime());
        v.setDtype(rs.getString("dtype"));
        v.setRazaoSocial(rs.getString("razao_social"));
        v.setCnpjCpf(rs.getString("cnpj_cpf"));
        return v;
    };

    private static final String INSERT_USUARIO_SQL = """
            INSERT INTO usuario (nome, email, senha_hash, role, ativo, data_cadastro, data_atualizacao, dtype)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String INSERT_VENDEDOR_SQL = "INSERT INTO vendedor (id, razao_social, cnpj_cpf) VALUES (?, ?, ?)";

    private static final String FIND_BY_ID_SQL = """
            SELECT u.id, u.nome, u.email, u.senha_hash, u.role, u.ativo,
                   u.data_cadastro, u.data_atualizacao, u.dtype,
                   v.razao_social, v.cnpj_cpf
            FROM usuario u JOIN vendedor v ON u.id = v.id
            WHERE u.id = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT u.id, u.nome, u.email, u.senha_hash, u.role, u.ativo,
                   u.data_cadastro, u.data_atualizacao, u.dtype,
                   v.razao_social, v.cnpj_cpf
            FROM usuario u JOIN vendedor v ON u.id = v.id
            """;

    private static final String UPDATE_USUARIO_SQL = """
            UPDATE usuario SET nome = ?, email = ?, senha_hash = ?, role = ?, ativo = ?,
                data_atualizacao = ?, dtype = ? WHERE id = ?
            """;

    private static final String UPDATE_VENDEDOR_SQL = "UPDATE vendedor SET razao_social = ?, cnpj_cpf = ? WHERE id = ?";

    private static final String DELETE_VENDEDOR_SQL = "DELETE FROM vendedor WHERE id = ?";

    private static final String DELETE_USUARIO_SQL = "DELETE FROM usuario WHERE id = ?";

    @Override
    public void save(Vendedor vendedor) {
        vendedor.onCreate();
        vendedor.setRole("VENDEDOR");
        vendedor.setAtivo(true);
        vendedor.setDtype("VENDEDOR");

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_USUARIO_SQL)) {
                stmt.setString(1, vendedor.getNome());
                stmt.setString(2, vendedor.getEmail());
                stmt.setString(3, vendedor.getSenhaHash());
                stmt.setString(4, vendedor.getRole());
                stmt.setBoolean(5, vendedor.isAtivo());
                stmt.setTimestamp(6, Timestamp.valueOf(vendedor.getDataCadastro()));
                stmt.setTimestamp(7, Timestamp.valueOf(vendedor.getDataAtualizacao()));
                stmt.setString(8, vendedor.getDtype());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        vendedor.setId(rs.getLong("id"));
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(INSERT_VENDEDOR_SQL)) {
                stmt.setLong(1, vendedor.getId());
                stmt.setString(2, vendedor.getRazaoSocial());
                stmt.setString(3, vendedor.getCnpjCpf());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public Vendedor findById(Long id) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_SQL)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return VENDEDOR_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Vendedor> findAll() {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Vendedor> vendedores = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(FIND_ALL_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        vendedores.add(VENDEDOR_MAPPER.mapRow(rs));
                    }
                }
            }
            return vendedores;
        });
    }

    @Override
    public void update(Vendedor vendedor) {
        vendedor.onUpdate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_USUARIO_SQL)) {
                stmt.setString(1, vendedor.getNome());
                stmt.setString(2, vendedor.getEmail());
                stmt.setString(3, vendedor.getSenhaHash());
                stmt.setString(4, vendedor.getRole());
                stmt.setBoolean(5, vendedor.isAtivo());
                stmt.setTimestamp(6, Timestamp.valueOf(vendedor.getDataAtualizacao()));
                stmt.setString(7, vendedor.getDtype());
                stmt.setLong(8, vendedor.getId());
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_VENDEDOR_SQL)) {
                stmt.setString(1, vendedor.getRazaoSocial());
                stmt.setString(2, vendedor.getCnpjCpf());
                stmt.setLong(3, vendedor.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Vendedor vendedor) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_VENDEDOR_SQL)) {
                stmt.setLong(1, vendedor.getId());
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_USUARIO_SQL)) {
                stmt.setLong(1, vendedor.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void deleteAll() {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM vendedor")) { stmt.executeUpdate(); }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM usuario WHERE dtype = 'VENDEDOR'")) { stmt.executeUpdate(); }
        });
    }
}