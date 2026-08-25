package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.AdminDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Admin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class AdminDAOImpl extends AbstractDAOImpl<Admin> implements AdminDAO {

    private static final RowMapper<Admin> ADMIN_MAPPER = rs -> {
        Admin a = new Admin();
        a.setId(rs.getLong("id"));
        a.setNome(rs.getString("nome"));
        a.setEmail(rs.getString("email"));
        a.setSenhaHash(rs.getString("senha_hash"));
        a.setRole(rs.getString("role"));
        a.setAtivo(rs.getBoolean("ativo"));
        a.setDataCadastro(rs.getTimestamp("data_cadastro").toLocalDateTime());
        a.setDataAtualizacao(rs.getTimestamp("data_atualizacao").toLocalDateTime());
        a.setDtype(rs.getString("dtype"));
        return a;
    };

    private static final String INSERT_USUARIO_SQL = """
            INSERT INTO usuario (nome, email, senha_hash, role, ativo, data_cadastro, data_atualizacao, dtype)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String INSERT_ADMIN_SQL = "INSERT INTO admin (id) VALUES (?)";

    private static final String FIND_BY_ID_SQL = """
            SELECT u.* FROM usuario u JOIN admin a ON u.id = a.id WHERE u.id = ?
            """;

    private static final String FIND_ALL_SQL = """
            SELECT u.* FROM usuario u JOIN admin a ON u.id = a.id
            """;

    private static final String UPDATE_USUARIO_SQL = """
            UPDATE usuario SET nome = ?, email = ?, senha_hash = ?, role = ?, ativo = ?,
                data_atualizacao = ?, dtype = ? WHERE id = ?
            """;

    private static final String DELETE_ADMIN_SQL = "DELETE FROM admin WHERE id = ?";

    private static final String DELETE_USUARIO_SQL = "DELETE FROM usuario WHERE id = ?";

    @Override
    public void save(Admin admin) {
        admin.onCreate();
        admin.setRole("ADMIN");
        admin.setAtivo(true);
        admin.setDtype("ADMIN");

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_USUARIO_SQL)) {
                stmt.setString(1, admin.getNome());
                stmt.setString(2, admin.getEmail());
                stmt.setString(3, admin.getSenhaHash());
                stmt.setString(4, admin.getRole());
                stmt.setBoolean(5, admin.isAtivo());
                stmt.setTimestamp(6, Timestamp.valueOf(admin.getDataCadastro()));
                stmt.setTimestamp(7, Timestamp.valueOf(admin.getDataAtualizacao()));
                stmt.setString(8, admin.getDtype());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        admin.setId(rs.getLong("id"));
                    }
                }
            }

            try (PreparedStatement stmt = conn.prepareStatement(INSERT_ADMIN_SQL)) {
                stmt.setLong(1, admin.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public Admin findById(Long id) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_SQL)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return ADMIN_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Admin> findAll() {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Admin> admins = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(FIND_ALL_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        admins.add(ADMIN_MAPPER.mapRow(rs));
                    }
                }
            }
            return admins;
        });
    }

    @Override
    public void update(Admin admin) {
        admin.onUpdate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_USUARIO_SQL)) {
                stmt.setString(1, admin.getNome());
                stmt.setString(2, admin.getEmail());
                stmt.setString(3, admin.getSenhaHash());
                stmt.setString(4, admin.getRole());
                stmt.setBoolean(5, admin.isAtivo());
                stmt.setTimestamp(6, Timestamp.valueOf(admin.getDataAtualizacao()));
                stmt.setString(7, admin.getDtype());
                stmt.setLong(8, admin.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Admin admin) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_ADMIN_SQL)) {
                stmt.setLong(1, admin.getId());
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_USUARIO_SQL)) {
                stmt.setLong(1, admin.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void deleteAll() {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM admin")) { stmt.executeUpdate(); }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM usuario WHERE dtype = 'ADMIN'")) { stmt.executeUpdate(); }
        });
    }
}