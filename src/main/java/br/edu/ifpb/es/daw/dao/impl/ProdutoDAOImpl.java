package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.ProdutoDAO;
import br.edu.ifpb.es.daw.dao.RowMapper;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.dao.VendedorDAO;
import br.edu.ifpb.es.daw.entities.Produto;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO JDBC de produto (issues #29 e #8).
 *
 * ============================================================
 * DECISÃO: como o RowMapper carrega Categoria e Vendedor
 * ============================================================
 * A issue #8 pede que o RowMapper de Produto traga Vendedor e Categoria e
 * que a escolha entre JOIN único e carga sob demanda seja documentada.
 * A resposta não é a mesma para as duas associações, e quem decide é o RLS
 * — não a preferência de estilo.
 *
 * 1. Categoria -> JOIN ÚNICO (eager, sempre)
 *    - A policy de leitura é categoria_select ... USING (true): o catálogo
 *      é vitrine pública (03_rls_categoria_produto.sql).
 *    - produto.id_categoria é NOT NULL e a FK é ON DELETE RESTRICT, então
 *      toda linha de produto tem exatamente uma categoria viva.
 *    - Somando as duas coisas: o INNER JOIN nunca descarta um produto e
 *      nunca devolve categoria vazia, em NENHUM contexto de tenant.
 *    - Custo: 3 colunas a mais por linha, contra 1 query extra por produto
 *      (N+1) se fosse sob demanda. O JOIN ganha com folga numa listagem de
 *      vitrine, que é o caso de uso dominante.
 *
 * 2. Vendedor -> CARGA SOB DEMANDA (lazy, via carregarVendedor)
 *    - Os dados do vendedor moram em vendedor JOIN usuario, e as duas
 *      tabelas são FORCE ROW LEVEL SECURITY com policy restritiva
 *      (01_grants_app_ecommerce.sql + 04_fix_rls_identidade.sql):
 *      USING (id = app.usuario_id OR app.usuario_role = ADMIN).
 *    - Um INNER JOIN produto -> vendedor -> usuario dentro do findAll()
 *      seria FILTRADO pelo RLS e zeraria a vitrine: a sessão anônima e o
 *      vendedor concorrente passariam a ver 0 produtos. O catálogo público
 *      simplesmente deixaria de existir. Trocar por LEFT JOIN devolveria as
 *      linhas, mas com todas as colunas do vendedor nulas — pagando o custo
 *      do JOIN para não trazer dado nenhum na maioria das requisições.
 *    - Portanto o vendedor fica fora do SELECT do catálogo. Quem precisa
 *      dele (o próprio vendedor no painel da loja, ou um ADMIN) chama
 *      carregarVendedor(), que reusa o VendedorDAO e volta a passar pelo
 *      RLS — sem furar o isolamento e sem espalhar dados de identidade por
 *      uma consulta pública.
 *
 * Contrapartida assumida: carregarVendedor() aplicado a uma lista é N+1.
 * É aceitável porque o caminho quente (vitrine) não carrega vendedor, e o
 * caminho que carrega (painel da loja) opera sobre os produtos de um único
 * vendedor. Se virar gargalo, a saída é uma carga em lote (id IN (...)),
 * não trocar a estratégia por JOIN.
 *
 * A FK crua (idVendedor) continua sempre preenchida, então nada no domínio
 * depende de o objeto Vendedor ter sido materializado.
 */
public class ProdutoDAOImpl extends AbstractDAOImpl<Produto> implements ProdutoDAO {

    private final VendedorDAO vendedorDAO;

    public ProdutoDAOImpl() {
        this(new VendedorDAOImpl());
    }

    /** Construtor de injeção — útil para teste e para trocar a implementação. */
    public ProdutoDAOImpl(VendedorDAO vendedorDAO) {
        this.vendedorDAO = vendedorDAO;
    }

    /**
     * Mapeia produto + categoria (JOIN único). O vendedor NÃO é mapeado aqui
     * de propósito: ver a decisão no javadoc da classe.
     */
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
        // Categoria vem no mesmo ResultSet, com aliases prefixados.
        p.setCategoria(CategoriaDAOImpl.mapRow(rs, "categoria_"));
        return p;
    };

    private static final String INSERT_SQL = """
            INSERT INTO produto (nome, descricao, estoque, preco, id_vendedor, id_categoria, data_cadastro, data_atualizacao)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id_produto
            """;

    /**
     * Projeção única do catálogo: produto + categoria.
     *
     * Os aliases categoria_* existem porque produto e categoria têm colunas
     * homônimas (nome, descricao) — sem apelidar, o ResultSet devolveria a
     * primeira delas para os dois mapeamentos.
     */
    private static final String SELECT_BASE_SQL = """
            SELECT p.id_produto, p.nome, p.descricao, p.estoque, p.preco,
                   p.id_vendedor, p.id_categoria, p.data_cadastro, p.data_atualizacao,
                   c.id        AS categoria_id,
                   c.nome      AS categoria_nome,
                   c.descricao AS categoria_descricao
            FROM produto p
            JOIN categoria c ON c.id = p.id_categoria
            """;

    private static final String FIND_BY_ID_SQL = SELECT_BASE_SQL + " WHERE p.id_produto = ?";

    private static final String FIND_ALL_SQL = SELECT_BASE_SQL + " ORDER BY p.id_produto";

    private static final String FIND_BY_VENDEDOR_SQL =
            SELECT_BASE_SQL + " WHERE p.id_vendedor = ? ORDER BY p.id_produto";

    private static final String FIND_BY_CATEGORIA_SQL =
            SELECT_BASE_SQL + " WHERE p.id_categoria = ? ORDER BY p.id_produto";

    private static final String UPDATE_SQL = """
            UPDATE produto
            SET nome = ?, descricao = ?, estoque = ?, preco = ?, id_vendedor = ?, id_categoria = ?, data_atualizacao = ?
            WHERE id_produto = ?
            """;

    private static final String UPDATE_ESTOQUE_SQL = """
            UPDATE produto
            SET estoque = ?, data_atualizacao = ?
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
    public List<Produto> findByVendedor(Long idVendedor) {
        return findByFk(FIND_BY_VENDEDOR_SQL, idVendedor);
    }

    @Override
    public List<Produto> findByCategoria(Long idCategoria) {
        return findByFk(FIND_BY_CATEGORIA_SQL, idCategoria);
    }

    /** findByVendedor e findByCategoria só diferem no SQL: um parâmetro, uma FK. */
    private List<Produto> findByFk(String sql, Long valorFk) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            List<Produto> produtos = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, valorFk);
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
    public boolean atualizarEstoque(Long idProduto, int novoEstoque) {
        if (novoEstoque < 0) {
            // A tabela tem CHECK (estoque >= 0). Barrar aqui troca um
            // check_violation opaco por um erro de programação explícito.
            throw new IllegalArgumentException("Estoque não pode ser negativo: " + novoEstoque);
        }

        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(UPDATE_ESTOQUE_SQL)) {
                stmt.setInt(1, novoEstoque);
                stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setLong(3, idProduto);
                // 0 linhas = produto inexistente OU produto de outro vendedor
                // (produto_update filtra por id_vendedor). O RLS nega em
                // silêncio no UPDATE; o row count é o que expõe isso.
                return stmt.executeUpdate() > 0;
            }
        });
    }

    @Override
    public Produto carregarVendedor(Produto produto) {
        if (produto == null || produto.getIdVendedor() == null) {
            return produto;
        }
        // Transação própria: o VendedorDAO repropaga o contexto RLS, então a
        // leitura de identidade continua submetida às policies da issue #4.
        produto.setVendedor(vendedorDAO.findById(produto.getIdVendedor()));
        return produto;
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
