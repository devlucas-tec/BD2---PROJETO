package br.edu.ifpb.es.daw;

import br.edu.ifpb.es.daw.context.TenantContext;
import br.edu.ifpb.es.daw.dao.*;
import br.edu.ifpb.es.daw.dao.impl.*;
import br.edu.ifpb.es.daw.entities.*;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.*;

import java.io.File;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração da issue #10: DAOs de Cupom e Avaliacao em JDBC.
 *
 * Critério de aceite: as operações de cupom e avaliação funcionam via JDBC
 * com o RLS ativo. O equivalente a "endpoint" neste projeto é a chamada de
 * DAO dentro de um contexto montado pelo JwtAuthenticationFilter — não há
 * camada HTTP desde a issue #2.
 *
 * Complementa CupomAvaliacaoRlsIntegrationTest (issue #9), que testa as
 * policies em SQL cru. Aqui o alvo é a camada Java: mapeamento, agregação,
 * carimbo de data e o comportamento dos DAOs sob cada identidade.
 *
 * Pré-requisitos: .env na raiz e scripts 01..05 aplicados.
 * Sem isso a classe é pulada, não quebrada.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CupomAvaliacaoDaoIntegrationTest {

    private static final Long ID_ADMIN = 0L; // ADMIN é liberado pelo role, não pelo id

    private static final CupomDAO CUPOM_DAO = new CupomDAOImpl();
    private static final AvaliacaoDAO AVALIACAO_DAO = new AvaliacaoDAOImpl();
    private static final ClienteDAO CLIENTE_DAO = new ClienteDAOImpl();
    private static final VendedorDAO VENDEDOR_DAO = new VendedorDAOImpl();
    private static final CategoriaDAO CATEGORIA_DAO = new CategoriaDAOImpl();
    private static final ProdutoDAO PRODUTO_DAO = new ProdutoDAOImpl();

    private static String sufixo;
    private static Cliente clienteA;
    private static Cliente clienteB;
    private static Vendedor vendedor;
    private static Categoria categoria;
    private static Produto produto;
    private static Cupom cupomValido;
    private static Cupom cupomInativo;
    private static Cupom cupomExpirado;
    private static Avaliacao avaliacaoDeA;
    private static Avaliacao avaliacaoDeB;

    @BeforeAll
    static void montarCenario() {
        Assumptions.assumeTrue(new File(".env").exists(),
                "Sem .env na raiz: teste de integração pulado (ver .env.example)");
        Assumptions.assumeTrue(tabelasExistem(),
                "Tabelas cupom/avaliacao ausentes: aplique src/main/sql/05_ddl_rls_cupom_avaliacao.sql");

        sufixo = String.valueOf(System.currentTimeMillis());

        clienteA = novoCliente("Cliente A #10", sufixo + "1");
        clienteB = novoCliente("Cliente B #10", sufixo + "2");
        vendedor = novoVendedor(sufixo);
        categoria = new Categoria();
        categoria.setNome("Categoria Issue 10 " + sufixo);
        produto = new Produto();
        cupomValido = novoCupom("I10-OK-" + sufixo, StatusCupom.ATIVO, 30);
        cupomInativo = novoCupom("I10-INATIVO-" + sufixo, StatusCupom.INATIVO, 30);
        cupomExpirado = novoCupom("I10-EXPIRADO-" + sufixo, StatusCupom.ATIVO, -1);

        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            CLIENTE_DAO.save(clienteA);
            CLIENTE_DAO.save(clienteB);
            VENDEDOR_DAO.save(vendedor);
            CATEGORIA_DAO.save(categoria);

            produto.setNome("Produto Issue 10 " + sufixo);
            produto.setEstoque(10);
            produto.setPreco(new BigDecimal("199.90"));
            produto.setIdVendedor(vendedor.getId());
            produto.setCategoria(categoria);
            PRODUTO_DAO.save(produto);

            CUPOM_DAO.save(cupomValido);
            CUPOM_DAO.save(cupomInativo);
            CUPOM_DAO.save(cupomExpirado);
        });

        assertNotNull(produto.getId(), "produto não recebeu id");
        assertNotNull(cupomValido.getId(), "cupom não recebeu id");
    }

    @AfterAll
    static void limparCenario() {
        if (sufixo == null) {
            return;
        }
        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            AVALIACAO_DAO.findByProduto(produto.getId()).forEach(AVALIACAO_DAO::delete);
            CUPOM_DAO.delete(cupomValido);
            CUPOM_DAO.delete(cupomInativo);
            CUPOM_DAO.delete(cupomExpirado);
            PRODUTO_DAO.delete(produto);
            CATEGORIA_DAO.delete(categoria);
            CLIENTE_DAO.delete(clienteA);
            CLIENTE_DAO.delete(clienteB);
            VENDEDOR_DAO.delete(vendedor);
        });
    }

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    // ============================================================
    // CupomDAO
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("findByCodigo encontra pela chave natural e mapeia todos os campos")
    void findByCodigoMapeiaOCupom() {
        Cupom achado = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.findByCodigo(cupomValido.getCodigo()));

        assertNotNull(achado);
        assertEquals(cupomValido.getId(), achado.getId());
        assertEquals(0, new BigDecimal("10.00").compareTo(achado.getValorDesconto()),
                "valor_desconto deve voltar como BigDecimal fiel");
        assertEquals(StatusCupom.ATIVO, achado.getStatus(), "status deve virar o enum StatusCupom");
        assertEquals(cupomValido.getDataExpiracao(), achado.getDataExpiracao(),
                "data_expiracao deve voltar como LocalDate");
    }

    @Test
    @Order(2)
    @DisplayName("findByCodigo devolve null quando o código não existe")
    void findByCodigoInexistente() {
        assertNull(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.findByCodigo("nao-existe-" + sufixo)));
    }

    @Test
    @Order(3)
    @DisplayName("findByCodigo depende do papel: CLIENTE não enxerga expirado nem inativo")
    void findByCodigoSofreOEfeitoDoRls() {
        assertNull(JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                        () -> CUPOM_DAO.findByCodigo(cupomExpirado.getCodigo())),
                "cupom_select esconde expirado do cliente");
        assertNull(JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                        () -> CUPOM_DAO.findByCodigo(cupomInativo.getCodigo())),
                "cupom_select esconde inativo do cliente");

        assertNotNull(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                        () -> CUPOM_DAO.findByCodigo(cupomExpirado.getCodigo())),
                "para ADMIN a policy é USING (true): ele enxerga o expirado");
    }

    @Test
    @Order(4)
    @DisplayName("findValidoByCodigo nega expirado MESMO para ADMIN — validação não é visibilidade")
    void findValidoNaoDependeDoPapel() {
        assertNull(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                        () -> CUPOM_DAO.findValidoByCodigo(cupomExpirado.getCodigo())),
                "é a diferença entre findByCodigo e findValidoByCodigo: "
                        + "o ADMIN enxerga o cupom vencido, mas não pode aplicá-lo");
        assertNull(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.findValidoByCodigo(cupomInativo.getCodigo())));

        assertNotNull(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.findValidoByCodigo(cupomValido.getCodigo())));
        assertNotNull(JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                () -> CUPOM_DAO.findValidoByCodigo(cupomValido.getCodigo())));
    }

    @Test
    @Order(5)
    @DisplayName("isExpirado compara com a data do banco")
    void isExpiradoUsaCurrentDate() {
        assertTrue(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.isExpirado(cupomExpirado)));
        assertFalse(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.isExpirado(cupomValido)));

        // Cupom que vence hoje ainda vale (o predicado é >= CURRENT_DATE).
        //
        // "Hoje" precisa vir do BANCO, não de LocalDate.now(): medido neste
        // ambiente, o Postgres roda em UTC e a aplicação em America/Fortaleza
        // (UTC-3), então entre 21h e a meia-noite locais o banco já virou o
        // dia. Montar o cupom com a data da aplicação faria este teste falhar
        // à noite e passar de manhã — e é exatamente essa divergência que
        // isExpirado existe para resolver.
        Cupom venceHoje = new Cupom();
        venceHoje.setCodigo("irrelevante");
        venceHoje.setValorDesconto(new BigDecimal("10.00"));
        venceHoje.setDataExpiracao(dataDoBanco());
        assertFalse(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                        () -> CUPOM_DAO.isExpirado(venceHoje)),
                "cupom que expira hoje (data do banco) ainda é utilizável");

        Cupom venceuOntem = new Cupom();
        venceuOntem.setCodigo("irrelevante");
        venceuOntem.setValorDesconto(new BigDecimal("10.00"));
        venceuOntem.setDataExpiracao(dataDoBanco().minusDays(1));
        assertTrue(JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                        () -> CUPOM_DAO.isExpirado(venceuOntem)),
                "um dia antes da data do banco já está expirado");
    }

    @Test
    @Order(6)
    @DisplayName("isExpirado rejeita cupom sem data de expiração")
    void isExpiradoRejeitaEntradaInvalida() {
        assertThrows(IllegalArgumentException.class, () -> CUPOM_DAO.isExpirado(null));
        assertThrows(IllegalArgumentException.class, () -> CUPOM_DAO.isExpirado(new Cupom()));
    }

    @Test
    @Order(7)
    @DisplayName("findValidos devolve o mesmo conjunto para ADMIN e CLIENTE")
    void findValidosIndependeDoPapel() {
        List<Cupom> comoAdmin = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                CUPOM_DAO::findValidos);
        List<Cupom> comoCliente = JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                CUPOM_DAO::findValidos);

        assertEquals(comoAdmin.size(), comoCliente.size(),
                "o predicado está no SQL, não na policy — os dois veem o mesmo");
        assertTrue(comoCliente.stream().anyMatch(c -> c.getId().equals(cupomValido.getId())));
        assertTrue(comoAdmin.stream().noneMatch(c -> c.getId().equals(cupomExpirado.getId())));
        assertTrue(comoAdmin.stream().noneMatch(c -> c.getId().equals(cupomInativo.getId())));
    }

    @Test
    @Order(8)
    @DisplayName("update de cupom é exclusivo de ADMIN e some da vista do cliente ao desativar")
    void updateDeCupom() {
        Cupom alvo = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.findById(cupomInativo.getId()));
        alvo.setValorDesconto(new BigDecimal("25.50"));

        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> CUPOM_DAO.update(alvo));
        Cupom depois = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.findById(cupomInativo.getId()));
        assertEquals(0, new BigDecimal("25.50").compareTo(depois.getValorDesconto()));

        // O mesmo update como CLIENTE não alcança linha nenhuma
        alvo.setValorDesconto(new BigDecimal("99.99"));
        JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                () -> CUPOM_DAO.update(alvo));
        Cupom inalterado = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> CUPOM_DAO.findById(cupomInativo.getId()));
        assertEquals(0, new BigDecimal("25.50").compareTo(inalterado.getValorDesconto()),
                "cupom_update exige ADMIN: o RLS nega em silêncio");
    }

    // ============================================================
    // AvaliacaoDAO
    // ============================================================

    @Test
    @Order(9)
    @DisplayName("save preenche data_avaliacao explicitamente e devolve no próprio objeto")
    void saveCarimbaDataAvaliacao() {
        LocalDateTime antes = LocalDateTime.now().minusMinutes(1);

        avaliacaoDeA = new Avaliacao();
        avaliacaoDeA.setNota(5);
        avaliacaoDeA.setComentario("Excelente");
        avaliacaoDeA.setIdCliente(clienteA.getId());
        avaliacaoDeA.setIdProduto(produto.getId());

        JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                () -> AVALIACAO_DAO.save(avaliacaoDeA));

        assertNotNull(avaliacaoDeA.getId(), "INSERT ... RETURNING deveria devolver o id");
        assertNotNull(avaliacaoDeA.getDataAvaliacao(),
                "o objeto precisa sair do save já com a data — é o ponto de preencher no INSERT");
        assertTrue(avaliacaoDeA.getDataAvaliacao().isAfter(antes));

        Avaliacao doBanco = JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.findById(avaliacaoDeA.getId()));
        assertEquals(avaliacaoDeA.getDataAvaliacao(), doBanco.getDataAvaliacao(),
                "a data gravada tem de ser a mesma do objeto, não o now() do banco");
        assertEquals(clienteA.getId(), doBanco.getIdCliente());
        assertEquals(produto.getId(), doBanco.getIdProduto());
    }

    @Test
    @Order(10)
    @DisplayName("Cliente não assina avaliação com o id de outro cliente")
    void naoAssinaEmNomeDeOutro() {
        Avaliacao forjada = new Avaliacao();
        forjada.setNota(1);
        forjada.setComentario("Forjada");
        forjada.setIdCliente(clienteB.getId());
        forjada.setIdProduto(produto.getId());

        assertThrows(RuntimeException.class,
                () -> JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                        () -> AVALIACAO_DAO.save(forjada)),
                "avaliacao_insert compara id_cliente com app.usuario_id");
        assertNull(forjada.getId(), "nada pode ter sido gravado");
    }

    @Test
    @Order(11)
    @DisplayName("findByProduto e findByCliente filtram, e a leitura é pública")
    void consultasDeAvaliacao() {
        avaliacaoDeB = new Avaliacao();
        avaliacaoDeB.setNota(3);
        avaliacaoDeB.setComentario("Razoavel");
        avaliacaoDeB.setIdCliente(clienteB.getId());
        avaliacaoDeB.setIdProduto(produto.getId());
        JwtAuthenticationFilter.executeAuthenticated(clienteB.getId(), "CLIENTE",
                () -> AVALIACAO_DAO.save(avaliacaoDeB));

        List<Avaliacao> doProduto = JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.findByProduto(produto.getId()));
        assertEquals(2, doProduto.size(), "avaliacao_select é USING (true): anônimo lê tudo");
        assertTrue(doProduto.stream().allMatch(a -> produto.getId().equals(a.getIdProduto())));

        List<Avaliacao> doClienteA = JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.findByCliente(clienteA.getId()));
        assertEquals(1, doClienteA.size());
        assertEquals(avaliacaoDeA.getId(), doClienteA.get(0).getId());
    }

    @Test
    @Order(12)
    @DisplayName("mediaNotasPorProduto calcula no banco e distingue vazio de zero")
    void mediaDeNotas() {
        OptionalDouble media = JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.mediaNotasPorProduto(produto.getId()));
        assertTrue(media.isPresent());
        assertEquals(4.0, media.getAsDouble(), 0.0001, "média de 5 e 3");

        assertEquals(2, JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.contarPorProduto(produto.getId())));

        OptionalDouble semNada = JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.mediaNotasPorProduto(-1L));
        assertFalse(semNada.isPresent(),
                "AVG de conjunto vazio é NULL: produto sem avaliação não tem média zero");
        assertEquals(0, JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.contarPorProduto(-1L)));
    }

    @Test
    @Order(13)
    @DisplayName("Só o autor altera a própria avaliação")
    void updateSoPeloAutor() {
        avaliacaoDeA.setNota(4);
        avaliacaoDeA.setComentario("Revisando");
        JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                () -> AVALIACAO_DAO.update(avaliacaoDeA));
        assertEquals(4, JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.findById(avaliacaoDeA.getId())).getNota());

        Avaliacao copiaDeB = JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.findById(avaliacaoDeB.getId()));
        copiaDeB.setNota(1);
        JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                () -> AVALIACAO_DAO.update(copiaDeB));
        assertEquals(3, JwtAuthenticationFilter.executeAnonymous(
                        () -> AVALIACAO_DAO.findById(avaliacaoDeB.getId())).getNota(),
                "RLS nega em silêncio: a nota de B não muda");

        // A média não pode ter mudado por causa da tentativa frustrada
        assertEquals(3.5, JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.mediaNotasPorProduto(produto.getId())).getAsDouble(), 0.0001);
    }

    @Test
    @Order(14)
    @DisplayName("Transferir a autoria da avaliação é recusado pelo WITH CHECK")
    void naoTransfereAutoria() {
        Avaliacao roubada = JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.findById(avaliacaoDeA.getId()));
        roubada.setIdCliente(clienteB.getId());

        assertThrows(RuntimeException.class,
                () -> JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                        () -> AVALIACAO_DAO.update(roubada)));
    }

    @Test
    @Order(15)
    @DisplayName("Só o autor apaga a própria avaliação")
    void deleteSoPeloAutor() {
        JwtAuthenticationFilter.executeAuthenticated(clienteA.getId(), "CLIENTE",
                () -> AVALIACAO_DAO.delete(avaliacaoDeB));
        assertNotNull(JwtAuthenticationFilter.executeAnonymous(
                        () -> AVALIACAO_DAO.findById(avaliacaoDeB.getId())),
                "A não pode apagar a avaliação de B");

        JwtAuthenticationFilter.executeAuthenticated(clienteB.getId(), "CLIENTE",
                () -> AVALIACAO_DAO.delete(avaliacaoDeB));
        assertNull(JwtAuthenticationFilter.executeAnonymous(
                () -> AVALIACAO_DAO.findById(avaliacaoDeB.getId())));
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static boolean tabelasExistem() {
        try {
            return TransactionalDataAccess.executeInTransaction(conn -> {
                String sql = "SELECT count(*) FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_name IN ('cupom', 'avaliacao')";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getInt(1) == 2;
                }
            });
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * CURRENT_DATE do banco. Necessário porque o Postgres do Supabase roda em
     * UTC e a aplicação em America/Fortaleza — as duas datas divergem por
     * algumas horas todo dia, e é a do banco que as policies enxergam.
     */
    private static LocalDate dataDoBanco() {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement("SELECT CURRENT_DATE");
                 ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getDate(1).toLocalDate();
            }
        });
    }

    private static Cupom novoCupom(String codigo, StatusCupom status, int diasAteExpirar) {
        Cupom c = new Cupom();
        c.setCodigo(codigo);
        c.setValorDesconto(new BigDecimal("10.00"));
        // Ancorado na data do BANCO, não na da aplicação: ver dataDoBanco().
        // Com LocalDate.now() a fixture ficaria a um dia de distância da
        // referência que as policies usam, e o teste oscilaria com a hora.
        c.setDataExpiracao(dataDoBanco().plusDays(diasAteExpirar));
        c.setStatus(status);
        return c;
    }

    private static Cliente novoCliente(String nome, String sufixoEmail) {
        Cliente c = new Cliente();
        c.setNome(nome);
        c.setEmail("issue10." + sufixoEmail + "@exemplo.local");
        c.setSenhaHash("hash");
        c.setTelefone("(83) 90000-0000");
        return c;
    }

    private static Vendedor novoVendedor(String sufixoDoc) {
        Vendedor v = new Vendedor();
        v.setNome("Vendedor Issue 10");
        v.setEmail("vendedor.issue10." + sufixoDoc + "@exemplo.local");
        v.setSenhaHash("hash");
        v.setRazaoSocial("Loja Issue 10 LTDA");
        v.setCnpjCpf(sufixoDoc);
        return v;
    }
}
