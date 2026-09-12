package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.CarrinhoDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Carrinho;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO JDBC de carrinho (issue #13). Tabela e policies vêm da issue #12
 * (09_rls_carrinho_item_carrinho.sql).
 *
 * ============================================================
 * PK simples aqui, composta em ItemCarrinhoDAO
 * ============================================================
 * carrinho tem PK própria (id BIGINT, via seq_carrinho_id), então continua
 * fazendo sentido implementar o DAO<Carrinho> genérico (save/findById/...).
 * Quem não tem PK própria é item_carrinho (PK composta carrinho_id +
 * produto_id) — por isso ItemCarrinhoDAO NÃO estende DAO<ItemCarrinho>;
 * ver o javadoc de ItemCarrinhoDAO.
 *
 * ============================================================
 * criarParaCliente: get-or-create, não apenas insert
 * ============================================================
 * uq_carrinho_cliente garante 1 carrinho por cliente no banco, mas deixar
 * o UNIQUE ser a única linha de defesa faria toda chamada repetida (ex.:
 * cliente clica "adicionar ao carrinho" antes de ter carrinho) estourar
 * unique_violation. Por isso o método primeiro consulta (findByCliente) e
 * só faz INSERT se realmente não existir. Ainda assim, duas requisições
 * concorrentes podem colidir entre a consulta e o insert — nesse caso o
 * catch abaixo trata o unique_violation (SQLState 23505) como "alguém
 * criou primeiro" e devolve o carrinho já existente, em vez de propagar
 * o erro.
 */
public class CarrinhoDAOImpl extends AbstractDAOImpl<Carrinho> implements CarrinhoDAO {

    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";

    private static final RowMapper<Carrinho> CARRINHO_MAPPER = rs -> {
        Carrinho c = new Carrinho();
        c.setId(rs.getLong("id"));
        Timestamp dataCriacao = rs.getTimestamp("data_criacao");
        if (dataCriacao != null) {
            c.setDataCriacao(dataCriacao.toLocalDateTime().toLocalDate());
        }
        Timestamp dataAtualizacao = rs.getTimestamp("data_ultima_atualizacao");
        if (dataAtualizacao != null) {
            c.setDataAtualizacao(dataAtualizacao.toLocalDateTime().toLocalDate());
        }
        c.setIdCliente(rs.getLong("id_cliente"));
        return c;
    };

    private static final String COLUNAS = "id, data_criacao, data_ultima_atualizacao, id_cliente";

    private static final String INSERT_SQL = """
            INSERT INTO carrinho (data_criacao, data_ultima_atualizacao, id_cliente)
            VALUES (?, ?, ?)
            RETURNING id
            """;

    private static final String FIND_BY_ID_SQL =
            "SELECT " + COLUNAS + " FROM carrinho WHERE id = ?";

    private static final String FIND_ALL_SQL =
            "SELECT " + COLUNAS + " FROM carrinho ORDER BY id";

    private static final String FIND_BY_CLIENTE_SQL =
            "SELECT " + COLUNAS + " FROM carrinho WHERE id_cliente = ?";

    private static final String UPDATE_SQL = """
            UPDATE carrinho
            SET data_criacao = ?, data_ultima_atualizacao = ?, id_cliente = ?
            WHERE id = ?
            """;

    private static final String TOCAR_SQL =
            "UPDATE carrinho SET data_ultima_atualizacao = ? WHERE id = ?";

    private static final String DELETE_SQL = "DELETE FROM carrinho WHERE id = ?";
    private static final String DELETE_ALL_SQL = "DELETE FROM carrinho";

    @Override
    public void save(Carrinho carrinho) {
        carrinho.onCreate();
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setTimestamp(1, Timestamp.valueOf(carrinho.getDataCriacao().atStartOfDay()));
                stmt.setTimestamp(2, Timestamp.valueOf(carrinho.getDataAtualizacao().atStartOfDay()));
                stmt.setLong(3, carrinho.getIdCliente());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        carrinho.setId(rs.getLong("id"));
                    }
                    // Sem linha: carrinho_insert exige id_cliente = usuario
                    // autenticado. O INSERT foi recusado pelo RLS — id fica null.
                }
            }
        });
    }

    @Override
    public Carrinho findById(Long id) {
        return buscarUm(FIND_BY_ID_SQL, id);
    }

    @Override
    public List<Carrinho> findAll() {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Carrinho> carrinhos = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(FIND_ALL_SQL)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        carrinhos.add(CARRINHO_MAPPER.mapRow(rs));
                    }
                }
            }
            return carrinhos;
        });
    }

    @Override
    public Carrinho findByCliente(Long idCliente) {
        return buscarUm(FIND_BY_CLIENTE_SQL, idCliente);
    }

    @Override
    public Carrinho criarParaCliente(Long idCliente) {
        Carrinho existente = findByCliente(idCliente);
        if (existente != null) {
            return existente;
        }

        Carrinho novo = new Carrinho();
        novo.setIdCliente(idCliente);
        try {
            save(novo);
        } catch (RuntimeException e) {
            if (isUniqueViolation(e)) {
                // Corrida: outra requisição criou o carrinho entre o
                // findByCliente acima e este INSERT. Devolve o já existente
                // em vez de propagar o erro.
                return findByCliente(idCliente);
            }
            throw e;
        }
        return novo;
    }

    @Override
    public void atualizarDataAtualizacao(Long idCarrinho) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(TOCAR_SQL)) {
                stmt.setTimestamp(1, Timestamp.valueOf(java.time.LocalDateTime.now()));
                stmt.setLong(2, idCarrinho);
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void update(Carrinho carrinho) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setTimestamp(1, Timestamp.valueOf(carrinho.getDataCriacao().atStartOfDay()));
                stmt.setTimestamp(2, Timestamp.valueOf(carrinho.getDataAtualizacao().atStartOfDay()));
                // id_cliente vai no SET de propósito: é o que dispara o
                // WITH CHECK da policy carrinho_update se alguém tentar
                // "trocar de dono" o carrinho.
                stmt.setLong(3, carrinho.getIdCliente());
                stmt.setLong(4, carrinho.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Carrinho carrinho) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setLong(1, carrinho.getId());
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

    private Carrinho buscarUm(String sql, Long parametro) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, parametro);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return CARRINHO_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    /**
     * TransactionalDataAccess embrulha toda SQLException em
     * RuntimeException("Erro na transação", causa) — por isso o unique
     * violation precisa ser desembrulhado da causa, não pego direto.
     */
    private static boolean isUniqueViolation(RuntimeException e) {
        Throwable causa = e.getCause();
        return causa instanceof SQLException sqlEx
                && SQLSTATE_UNIQUE_VIOLATION.equals(sqlEx.getSQLState());
    }
}
