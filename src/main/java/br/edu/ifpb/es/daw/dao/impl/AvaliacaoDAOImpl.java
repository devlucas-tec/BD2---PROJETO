package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.AvaliacaoDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.entities.Avaliacao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * DAO JDBC de avaliacao (issue #10). Tabela e policies vêm da issue #9.
 *
 * ============================================================
 * data_avaliacao é preenchida EXPLICITAMENTE no INSERT
 * ============================================================
 * A coluna tem DEFAULT now() no DDL, então omiti-la funcionaria. A issue
 * pede o preenchimento explícito, e o motivo é bom:
 *
 * - now() é o relógio do SERVIDOR DE BANCO. Deixar o default decidir espalha
 *   duas fontes de tempo pelo sistema (o Postgres aqui, LocalDateTime.now()
 *   em Produto e Usuario), e elas divergem em fuso e em drift de relógio.
 * - O objeto em memória ficaria com dataAvaliacao null depois do save, já
 *   que o valor teria nascido no banco. Quem chamou precisaria de um
 *   findById só para saber quando a própria avaliação foi criada.
 *
 * Passando o valor de Avaliacao.onCreate(), o objeto salvo e a linha gravada
 * carregam o mesmo instante — mesmo contrato já usado em ProdutoDAOImpl com
 * data_cadastro/data_atualizacao.
 *
 * Sobre o RLS: a leitura é pública (avaliacao_select USING (true)), então
 * findByProduto/findByCliente e as agregações respondem igual para qualquer
 * contexto, inclusive o anônimo. O aperto está na escrita — só o autor
 * (id_cliente = app.usuario_id) ou um ADMIN alcançam a linha.
 */
public class AvaliacaoDAOImpl extends AbstractDAOImpl<Avaliacao> implements AvaliacaoDAO {

    private static final RowMapper<Avaliacao> AVALIACAO_MAPPER = rs -> {
        Avaliacao a = new Avaliacao();
        a.setId(rs.getLong("id"));
        a.setNota(rs.getInt("nota"));
        a.setComentario(rs.getString("comentario"));
        Timestamp ts = rs.getTimestamp("data_avaliacao");
        if (ts != null) {
            a.setDataAvaliacao(ts.toLocalDateTime());
        }
        a.setIdCliente(rs.getLong("id_cliente"));
        a.setIdProduto(rs.getLong("id_produto"));
        return a;
    };

    private static final String COLUNAS =
            "id, nota, comentario, data_avaliacao, id_cliente, id_produto";

    private static final String INSERT_SQL = """
            INSERT INTO avaliacao (nota, comentario, data_avaliacao, id_cliente, id_produto)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String FIND_BY_ID_SQL =
            "SELECT " + COLUNAS + " FROM avaliacao WHERE id = ?";

    private static final String FIND_ALL_SQL =
            "SELECT " + COLUNAS + " FROM avaliacao ORDER BY id";

    private static final String FIND_BY_PRODUTO_SQL =
            "SELECT " + COLUNAS + " FROM avaliacao WHERE id_produto = ? ORDER BY data_avaliacao DESC, id DESC";

    private static final String FIND_BY_CLIENTE_SQL =
            "SELECT " + COLUNAS + " FROM avaliacao WHERE id_cliente = ? ORDER BY data_avaliacao DESC, id DESC";

    private static final String MEDIA_POR_PRODUTO_SQL =
            "SELECT AVG(nota) FROM avaliacao WHERE id_produto = ?";

    private static final String CONTAR_POR_PRODUTO_SQL =
            "SELECT count(*) FROM avaliacao WHERE id_produto = ?";

    // data_avaliacao fica de fora: é o carimbo de criação, e editar o texto
    // da avaliação não a torna mais recente.
    private static final String UPDATE_SQL = """
            UPDATE avaliacao
            SET nota = ?, comentario = ?, id_cliente = ?, id_produto = ?
            WHERE id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM avaliacao WHERE id = ?";
    private static final String DELETE_ALL_SQL = "DELETE FROM avaliacao";

    @Override
    public void save(Avaliacao avaliacao) {
        avaliacao.onCreate();
        // LocalDateTime tem precisão de nanossegundos; TIMESTAMP do PostgreSQL
        // guarda microssegundos e ARREDONDA o resto (medido: .337921700 vira
        // .337922). Sem truncar aqui, o objeto em memória sairia do save com
        // um instante que não existe na linha gravada — justamente o contrário
        // do que preencher data_avaliacao explicitamente pretende garantir.
        avaliacao.setDataAvaliacao(avaliacao.getDataAvaliacao().truncatedTo(ChronoUnit.MICROS));

        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setInt(1, avaliacao.getNota());
                stmt.setString(2, avaliacao.getComentario());
                stmt.setTimestamp(3, Timestamp.valueOf(avaliacao.getDataAvaliacao()));
                stmt.setLong(4, avaliacao.getIdCliente());
                stmt.setLong(5, avaliacao.getIdProduto());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        avaliacao.setId(rs.getLong("id"));
                    }
                }
            }
        });
    }

    @Override
    public Avaliacao findById(Long id) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(FIND_BY_ID_SQL)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return AVALIACAO_MAPPER.mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<Avaliacao> findAll() {
        return buscarLista(FIND_ALL_SQL, null);
    }

    @Override
    public List<Avaliacao> findByProduto(Long idProduto) {
        return buscarLista(FIND_BY_PRODUTO_SQL, idProduto);
    }

    @Override
    public List<Avaliacao> findByCliente(Long idCliente) {
        return buscarLista(FIND_BY_CLIENTE_SQL, idCliente);
    }

    @Override
    public OptionalDouble mediaNotasPorProduto(Long idProduto) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(MEDIA_POR_PRODUTO_SQL)) {
                stmt.setLong(1, idProduto);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        double media = rs.getDouble(1);
                        // AVG sobre conjunto vazio devolve NULL, e getDouble
                        // converte NULL em 0.0 — sem o wasNull(), produto sem
                        // avaliação passaria a ter "média zero".
                        if (!rs.wasNull()) {
                            return OptionalDouble.of(media);
                        }
                    }
                }
            }
            return OptionalDouble.empty();
        });
    }

    @Override
    public int contarPorProduto(Long idProduto) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(CONTAR_POR_PRODUTO_SQL)) {
                stmt.setLong(1, idProduto);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    @Override
    public void update(Avaliacao avaliacao) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_SQL)) {
                stmt.setInt(1, avaliacao.getNota());
                stmt.setString(2, avaliacao.getComentario());
                // id_cliente vai no SET de propósito: é o que dispara o
                // WITH CHECK da policy se alguém tentar transferir a autoria.
                stmt.setLong(3, avaliacao.getIdCliente());
                stmt.setLong(4, avaliacao.getIdProduto());
                stmt.setLong(5, avaliacao.getId());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public void delete(Avaliacao avaliacao) {
        TransactionalDataAccess.executeInTransactionVoid(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(DELETE_SQL)) {
                stmt.setLong(1, avaliacao.getId());
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

    private List<Avaliacao> buscarLista(String sql, Long parametro) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Avaliacao> avaliacoes = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (parametro != null) {
                    stmt.setLong(1, parametro);
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        avaliacoes.add(AVALIACAO_MAPPER.mapRow(rs));
                    }
                }
            }
            return avaliacoes;
        });
    }
}
