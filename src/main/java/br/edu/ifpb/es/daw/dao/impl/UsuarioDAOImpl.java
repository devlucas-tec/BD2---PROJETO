package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.dao.UsuarioDAO;
import br.edu.ifpb.es.daw.entities.Usuario;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class UsuarioDAOImpl extends AbstractDAOImpl<Usuario> implements UsuarioDAO {

    private static final RowMapper<Usuario> USUARIO_MAPPER = rs -> {
        Usuario u = new Usuario();
        u.setId(rs.getLong("id"));
        u.setNome(rs.getString("nome"));
        u.setEmail(rs.getString("email"));
        u.setSenhaHash(rs.getString("senha_hash"));
        u.setRole(rs.getString("role"));
        u.setAtivo(rs.getBoolean("ativo"));
        u.setDataCadastro(rs.getTimestamp("data_cadastro").toLocalDateTime());
        u.setDataAtualizacao(rs.getTimestamp("data_atualizacao").toLocalDateTime());
        u.setDtype(rs.getString("dtype"));
        return u;
    };

    private static final String INSERT_SQL = """
            INSERT INTO usuario (nome, email, senha_hash, role, ativo, data_cadastro, data_atualizacao, dtype)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String FIND_BY_ID_SQL = "SELECT * FROM usuario WHERE id = ?";

    private static final String FIND_ALL_SQL = "SELECT * FROM usuario";

    private static final String UPDATE_SQL = """
            UPDATE usuario
            SET nome = ?, email = ?, senha_hash = ?, role = ?, ativo = ?,
                data_atualizacao = ?, dtype = ?
            WHERE id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM usuario WHERE id = ?";

    private static final String FIND_BY_EMAIL_SQL = "SELECT * FROM usuario WHERE email = ?";

    private static final String EXISTS_BY_EMAIL_SQL = "SELECT 1 FROM usuario WHERE email = ? LIMIT 1";

    @Override
    public void save(Usuario usuario) {
        usuario.onCreate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, usuario.getNome());
                stmt.setString(2, usuario.getEmail());
                stmt.setString(3, usuario.getSenhaHash());
                stmt.setString(4, usuario.getRole());
                stmt.setBoolean(5, usuario.isAtivo());
                stmt.setTimestamp(6, Timestamp.valueOf(usuario.getDataCadastro()));
                stmt.setTimestamp(7, Timestamp.valueOf(usuario.getDataAtualizacao()));
                stmt.setString(8, usuario.getDtype());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        usuario.setId(rs.getLong("id"));
                    }
                }
            }
        });
    }

    @Override
    public Usuario findById(Long id) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_SQL)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return USUARIO_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Usuario> findAll() {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Usuario> usuarios = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(FIND_ALL_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        usuarios.add(USUARIO_MAPPER.mapRow(rs));
                    }
                }
            }
            return usuarios;
        });
    }

    @Override
    public void update(Usuario usuario) {
        usuario.onUpdate();

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setString(1, usuario.getNome());
                stmt.setString(2, usuario.getEmail());
                stmt.setString(3, usuario.getSenhaHash());
                stmt.setString(4, usuario.getRole());
                stmt.setBoolean(5, usuario.isAtivo());
                stmt.setTimestamp(6, Timestamp.valueOf(usuario.getDataAtualizacao()));
                stmt.setString(7, usuario.getDtype());
                stmt.setLong(8, usuario.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Usuario usuario) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setLong(1, usuario.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void deleteAll() {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM admin")) { stmt.executeUpdate(); }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM cliente")) { stmt.executeUpdate(); }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM vendedor")) { stmt.executeUpdate(); }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM usuario")) { stmt.executeUpdate(); }
        });
    }

    @Override
    public Usuario findByEmail(String email) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_EMAIL_SQL)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return USUARIO_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public boolean existsByEmail(String email) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(EXISTS_BY_EMAIL_SQL)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }
}