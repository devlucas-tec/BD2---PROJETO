package br.edu.ifpb.es.daw;

import br.edu.ifpb.es.daw.context.TenantContext;
import br.edu.ifpb.es.daw.dao.CategoriaDAO;
import br.edu.ifpb.es.daw.dao.ProdutoDAO;
import br.edu.ifpb.es.daw.dao.VendedorDAO;
import br.edu.ifpb.es.daw.dao.impl.CategoriaDAOImpl;
import br.edu.ifpb.es.daw.dao.impl.ProdutoDAOImpl;
import br.edu.ifpb.es.daw.dao.impl.VendedorDAOImpl;
import br.edu.ifpb.es.daw.entities.Categoria;
import br.edu.ifpb.es.daw.entities.Produto;
import br.edu.ifpb.es.daw.entities.Vendedor;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração da issue #8: DAOs de catálogo em JDBC, respeitando RLS.
 *
 * Cobre o critério de aceite ("/produtos e /categorias respondem corretamente
 * via JDBC, respeitando o RLS") no equivalente que este projeto tem a um
 * endpoint: a chamada de DAO dentro de um contexto montado pelo
 * JwtAuthenticationFilter.
 *
 * Pré-requisitos:
 * - Arquivo .env na raiz com DB_URL, DB_USER, DB_PASSWORD
 * - Scripts 01..04 de src/main/sql aplicados
 * - Conexão como app_ecommerce (role SEM BYPASSRLS — do contrário as policies
 *   não são avaliadas e o teste não prova nada)
 *
 * Sem .env a classe inteira é pulada, não quebrada.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogoDaoIntegrationTest {

    private static final Long ID_ADMIN = 0L; // ADMIN é liberado pelo role, não pelo id

    private static final CategoriaDAO CATEGORIA_DAO = new CategoriaDAOImpl();
    private static final ProdutoDAO PRODUTO_DAO = new ProdutoDAOImpl();
    private static final VendedorDAO VENDEDOR_DAO = new VendedorDAOImpl();

    private static String sufixo;
    private static Categoria categoria;
    private static Vendedor vendedorA;
    private static Vendedor vendedorB;
    private static Produto produto;

    @BeforeAll
    static void montarCenario() {
        Assumptions.assumeTrue(new File(".env").exists(),
                "Sem .env na raiz: teste de integração pulado (ver .env.example)");

        sufixo = String.valueOf(System.currentTimeMillis());

        categoria = new Categoria();
        categoria.setNome("Categoria Issue 8 " + sufixo);
        categoria.setDescricao("Removida no @AfterAll");

        vendedorA = novoVendedor("Loja A Issue 8", sufixo + "1");
        vendedorB = novoVendedor("Loja B Issue 8", sufixo + "2");

        // INSERT ... RETURNING também passa pela policy de SELECT: fora de um
        // contexto autorizado os ids voltariam null.
        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            CATEGORIA_DAO.save(categoria);
            VENDEDOR_DAO.save(vendedorA);
            VENDEDOR_DAO.save(vendedorB);
        });

        produto = new Produto();
        produto.setNome("Produto Issue 8 " + sufixo);
        produto.setDescricao("Removido no @AfterAll");
        produto.setEstoque(10);
        produto.setPreco(new BigDecimal("249.90"));
        produto.setIdVendedor(vendedorA.getId());
        produto.setCategoria(categoria);

        JwtAuthenticationFilter.executeAuthenticated(vendedorA.getId(), "VENDEDOR",
                () -> PRODUTO_DAO.save(produto));

        assertNotNull(categoria.getId(), "categoria não recebeu id");
        assertNotNull(vendedorA.getId(), "vendedorA não recebeu id");
        assertNotNull(produto.getId(), "produto não recebeu id");
    }

    @AfterAll
    static void limparCenario() {
        if (produto == null || produto.getId() == null) {
            return;
        }
        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            PRODUTO_DAO.delete(produto);
            VENDEDOR_DAO.delete(vendedorA);
            VENDEDOR_DAO.delete(vendedorB);
            CATEGORIA_DAO.delete(categoria);
        });
    }

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------
    // CategoriaDAO
    // ------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("CategoriaDAO.findByNome encontra pela chave natural, inclusive anônimo")
    void findByNomeDeveEncontrarCategoria() {
        Categoria encontrada = JwtAuthenticationFilter.executeAnonymous(
                () -> CATEGORIA_DAO.findByNome(categoria.getNome()));

        assertNotNull(encontrada, "categoria_select é vitrine pública: anônimo deve ler");
        assertEquals(categoria.getId(), encontrada.getId());
        assertEquals(categoria.getDescricao(), encontrada.getDescricao());
    }

    @Test
    @Order(2)
    @DisplayName("CategoriaDAO.findByNome devolve null para nome inexistente")
    void findByNomeDeveDevolverNullQuandoNaoExiste() {
        assertNull(JwtAuthenticationFilter.executeAnonymous(
                () -> CATEGORIA_DAO.findByNome("nao-existe-" + sufixo)));
    }

    @Test
    @Order(3)
    @DisplayName("categoria_update exige ADMIN: para VENDEDOR o UPDATE não alcança linha nenhuma")
    void updateDeCategoriaPorVendedorNaoDeveAlterarNada() {
        Categoria alvo = JwtAuthenticationFilter.executeAnonymous(
                () -> CATEGORIA_DAO.findById(categoria.getId()));
        String descricaoOriginal = alvo.getDescricao();

        alvo.setDescricao("alteracao indevida");
        JwtAuthenticationFilter.executeAuthenticated(vendedorA.getId(), "VENDEDOR",
                () -> CATEGORIA_DAO.update(alvo));

        Categoria depois = JwtAuthenticationFilter.executeAnonymous(
                () -> CATEGORIA_DAO.findById(categoria.getId()));
        assertEquals(descricaoOriginal, depois.getDescricao(),
                "RLS deveria ter negado o UPDATE em silêncio");
    }

    // ------------------------------------------------------------
    // ProdutoDAO — RowMapper e consultas
    // ------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("RowMapper de Produto traz a Categoria no mesmo SELECT (JOIN único)")
    void rowMapperDeveCarregarCategoriaEager() {
        Produto daVitrine = JwtAuthenticationFilter.executeAnonymous(
                () -> PRODUTO_DAO.findById(produto.getId()));

        assertNotNull(daVitrine, "produto_select é vitrine pública");
        assertNotNull(daVitrine.getCategoria(), "categoria deve vir carregada pelo JOIN");
        assertEquals(categoria.getId(), daVitrine.getCategoria().getId());
        assertEquals(categoria.getNome(), daVitrine.getCategoria().getNome());
        assertEquals(produto.getNome(), daVitrine.getNome(),
                "os aliases categoria_* não podem sobrescrever nome/descricao do produto");
    }

    @Test
    @Order(5)
    @DisplayName("Vendedor NÃO vem no SELECT do catálogo (carga sob demanda)")
    void rowMapperNaoDeveCarregarVendedor() {
        Produto daVitrine = JwtAuthenticationFilter.executeAnonymous(
                () -> PRODUTO_DAO.findById(produto.getId()));

        assertNull(daVitrine.getVendedor(), "vendedor é lazy por decisão de projeto");
        assertEquals(vendedorA.getId(), daVitrine.getIdVendedor(), "a FK crua continua disponível");
    }

    @Test
    @Order(6)
    @DisplayName("carregarVendedor respeita o RLS de identidade: null para anônimo, preenchido para o dono")
    void carregarVendedorDeveRespeitarRls() {
        Produto anonimo = JwtAuthenticationFilter.executeAnonymous(
                () -> PRODUTO_DAO.carregarVendedor(PRODUTO_DAO.findById(produto.getId())));
        assertNull(anonimo.getVendedor(),
                "vendedor_select só libera para o próprio usuário ou ADMIN");

        Produto peloDono = JwtAuthenticationFilter.executeAuthenticated(
                vendedorA.getId(), "VENDEDOR",
                () -> PRODUTO_DAO.carregarVendedor(PRODUTO_DAO.findById(produto.getId())));
        assertNotNull(peloDono.getVendedor(), "o dono enxerga a própria identidade");
        assertEquals(vendedorA.getId(), peloDono.getVendedor().getId());
        assertEquals(vendedorA.getRazaoSocial(), peloDono.getVendedor().getRazaoSocial());
    }

    @Test
    @Order(7)
    @DisplayName("findByVendedor e findByCategoria filtram pela FK")
    void findByVendedorEFindByCategoriaDevemFiltrar() {
        List<Produto> doA = JwtAuthenticationFilter.executeAnonymous(
                () -> PRODUTO_DAO.findByVendedor(vendedorA.getId()));
        assertTrue(doA.stream().anyMatch(p -> p.getId().equals(produto.getId())));
        assertTrue(doA.stream().allMatch(p -> vendedorA.getId().equals(p.getIdVendedor())));

        List<Produto> doB = JwtAuthenticationFilter.executeAnonymous(
                () -> PRODUTO_DAO.findByVendedor(vendedorB.getId()));
        assertTrue(doB.isEmpty(), "vendedor B não cadastrou nada");

        List<Produto> daCategoria = JwtAuthenticationFilter.executeAnonymous(
                () -> PRODUTO_DAO.findByCategoria(categoria.getId()));
        assertEquals(1, daCategoria.size());
        assertEquals(produto.getId(), daCategoria.get(0).getId());
        assertNotNull(daCategoria.get(0).getCategoria(), "o JOIN vale para toda a projeção");
    }

    // ------------------------------------------------------------
    // ProdutoDAO — atualizarEstoque
    // ------------------------------------------------------------

    @Test
    @Order(8)
    @DisplayName("atualizarEstoque: true para o dono, false para outro vendedor e para anônimo")
    void atualizarEstoqueDeveRespeitarODonoDaLinha() {
        boolean peloDono = JwtAuthenticationFilter.executeAuthenticated(
                vendedorA.getId(), "VENDEDOR",
                () -> PRODUTO_DAO.atualizarEstoque(produto.getId(), 42));
        assertTrue(peloDono, "produto_update libera o vendedor dono");

        boolean porOutro = JwtAuthenticationFilter.executeAuthenticated(
                vendedorB.getId(), "VENDEDOR",
                () -> PRODUTO_DAO.atualizarEstoque(produto.getId(), 999));
        assertFalse(porOutro, "RLS nega em silêncio: 0 linhas afetadas");

        boolean anonimo = JwtAuthenticationFilter.executeAnonymous(
                () -> PRODUTO_DAO.atualizarEstoque(produto.getId(), 999));
        assertFalse(anonimo, "sessão anônima não escreve no catálogo");

        Integer estoque = JwtAuthenticationFilter.executeAnonymous(
                () -> PRODUTO_DAO.findById(produto.getId()).getEstoque());
        assertEquals(42, estoque.intValue(), "só a atualização do dono pode ter valido");
    }

    @Test
    @Order(9)
    @DisplayName("atualizarEstoque devolve false para produto inexistente")
    void atualizarEstoqueDeveDevolverFalseParaProdutoInexistente() {
        boolean atualizou = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> PRODUTO_DAO.atualizarEstoque(-1L, 5));
        assertFalse(atualizou);
    }

    @Test
    @Order(10)
    @DisplayName("atualizarEstoque rejeita valor negativo antes de ir ao banco")
    void atualizarEstoqueDeveRejeitarNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> PRODUTO_DAO.atualizarEstoque(produto.getId(), -1));
    }

    private static Vendedor novoVendedor(String razaoSocial, String cnpjCpf) {
        Vendedor v = new Vendedor();
        v.setNome(razaoSocial);
        v.setEmail("issue8." + cnpjCpf + "@exemplo.local");
        v.setSenhaHash("hash");
        v.setRazaoSocial(razaoSocial + " LTDA");
        v.setCnpjCpf(cnpjCpf);
        return v;
    }
}
