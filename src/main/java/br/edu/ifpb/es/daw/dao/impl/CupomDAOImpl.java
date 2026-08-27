package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.CupomDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Cupom;
import br.edu.ifpb.es.daw.entities.StatusCupom;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO JDBC de cupom (issue #10). Tabela e policies vêm da issue #9.
 *
 * ============================================================
 * VALIDAÇÃO DE EXPIRAÇÃO: por que o RLS não basta
 * ============================================================
 * A policy cupom_select já esconde cupom expirado/inativo de quem não é
 * ADMIN. É tentador concluir que "se o findByCodigo devolveu algo, o cupom
 * vale" — e isso está errado por dois motivos:
 *
 * 1. Para um ADMIN a policy é USING (true): ele enxerga cupom expirado
 *    normalmente. Uma rotina administrativa que aplicasse desconto usando
 *    findByCodigo aceitaria cupom vencido sem reclamar.
 * 2. RLS é controle de ACESSO, não regra de negócio. Amarrar a validade do
 *    cupom à visibilidade significa que qualquer ajuste futuro na policy
 *    muda silenciosamente o comportamento do checkout.
 *
 * Por isso a validação é explícita e independente do papel:
 *   - findValidoByCodigo / findValidos aplicam o predicado no próprio SQL
 *   - isExpirado responde sobre um cupom já carregado
 *
 * Todos usam CURRENT_DATE (a data do BANCO), não LocalDate.now(). O relógio
 * e o fuso da aplicação podem divergir do Postgres, e é a data do banco que
 * as policies enxergam — validar com outra referência produziria um veredito
 * incoerente com o que o RLS faz.
 */
public class CupomDAOImpl extends AbstractDAOImpl<Cupom> implements CupomDAO {

    private static final RowMapper<Cupom> CUPOM_MAPPER = rs -> {
        Cupom c = new Cupom();
        c.setId(rs.getLong("id"));
        c.setCodigo(rs.getString("codigo"));
        c.setValorDesconto(rs.getBigDecimal("valor_desconto"));
        Date dataExpiracao = rs.getDate("data_expiracao");
        if (dataExpiracao != null) {
            c.setDataExpiracao(dataExpiracao.toLocalDate());
        }
        String status = rs.getString("status");
        if (status != null) {
            c.setStatus(StatusCupom.valueOf(status));
        }
        return c;
    };

    private static final String COLUNAS = "id, codigo, valor_desconto, data_expiracao, status";

    /** Predicado único de "cupom utilizável" — repetido em lugar nenhum. */
    private static final String UTILIZAVEL = "status = 'ATIVO' AND data_expiracao >= CURRENT_DATE";

    private static final String INSERT_SQL = """
            INSERT INTO cupom (codigo, valor_desconto, data_expiracao, status)
            VALUES (?, ?, ?, ?)
            RETURNING id
            """;

    private static final String FIND_BY_ID_SQL =
            "SELECT " + COLUNAS + " FROM cupom WHERE id = ?";

    private static final String FIND_BY_CODIGO_SQL =
            "SELECT " + COLUNAS + " FROM cupom WHERE codigo = ?";

    private static final String FIND_VALIDO_BY_CODIGO_SQL =
            "SELECT " + COLUNAS + " FROM cupom WHERE codigo = ? AND " + UTILIZAVEL;

    private static final String FIND_ALL_SQL =
            "SELECT " + COLUNAS + " FROM cupom ORDER BY id";

    private static final String FIND_VALIDOS_SQL =
            "SELECT " + COLUNAS + " FROM cupom WHERE " + UTILIZAVEL + " ORDER BY data_expiracao";

    private static final String IS_EXPIRADO_SQL =
            "SELECT CAST(? AS DATE) < CURRENT_DATE";

    private static final String UPDATE_SQL = """
            UPDATE cupom
            SET codigo = ?, valor_desconto = ?, data_expiracao = ?, status = ?
            WHERE id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM cupom WHERE id = ?";
    private static final String DELETE_ALL_SQL = "DELETE FROM cupom";

    @Override
    public void save(Cupom cupom) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, cupom.getCodigo());
                stmt.setBigDecimal(2, cupom.getValorDesconto());
                stmt.setDate(3, Date.valueOf(cupom.getDataExpiracao()));
                stmt.setString(4, statusDe(cupom));

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cupom.setId(rs.getLong("id"));
                    }
                    // Sem linha: cupom_insert exige ADMIN. O INSERT foi
                    // recusado ou o RETURNING veio filtrado — o id fica null.
                }
            }
        });
    }

    @Override
    public Cupom findById(Long id) {
        return buscarUm(FIND_BY_ID_SQL, id);
    }

    @Override
    public Cupom findByCodigo(String codigo) {
        return buscarUm(FIND_BY_CODIGO_SQL, codigo);
    }

    @Override
    public Cupom findValidoByCodigo(String codigo) {
        return buscarUm(FIND_VALIDO_BY_CODIGO_SQL, codigo);
    }

    @Override
    public List<Cupom> findAll() {
        return buscarLista(FIND_ALL_SQL);
    }

    @Override
    public List<Cupom> findValidos() {
        return buscarLista(FIND_VALIDOS_SQL);
    }

    @Override
    public boolean isExpirado(Cupom cupom) {
        if (cupom == null || cupom.getDataExpiracao() == null) {
            throw new IllegalArgumentException("Cupom sem data de expiração não pode ser avaliado");
        }
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(IS_EXPIRADO_SQL)) {
                stmt.setDate(1, Date.valueOf(cupom.getDataExpiracao()));
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getBoolean(1);
                }
            }
        });
    }

    @Override
    public void update(Cupom cupom) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setString(1, cupom.getCodigo());
                stmt.setBigDecimal(2, cupom.getValorDesconto());
                stmt.setDate(3, Date.valueOf(cupom.getDataExpiracao()));
                stmt.setString(4, statusDe(cupom));
                stmt.setLong(5, cupom.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Cupom cupom) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setLong(1, cupom.getId());
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

    /** A coluna é NOT NULL e a entidade já nasce ATIVO; o fallback é defensivo. */
    private static String statusDe(Cupom cupom) {
        return (cupom.getStatus() != null ? cupom.getStatus() : StatusCupom.ATIVO).name();
    }

    private Cupom buscarUm(String sql, Object parametro) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, parametro);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return CUPOM_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    private List<Cupom> buscarLista(String sql) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Cupom> cupons = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        cupons.add(CUPOM_MAPPER.mapRow(rs));
                    }
                }
            }
            return cupons;
        });
    }
}
