package br.edu.ifpb.es.daw;

import br.edu.ifpb.es.daw.context.TenantContext;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração da issue #9: RLS de cupom e avaliacao.
 *
 * Prova os dois critérios de aceite:
 *   1. Cliente não consegue criar avaliação assinada com o id de outro cliente
 *   2. Cupom expirado/inativo não aparece para cliente
 *
 * POR QUE ESTE TESTE EXISTE, se 05_ddl_rls_cupom_avaliacao.sql já traz um
 * bloco DO de validação: aquele bloco é PULADO quando executado no SQL Editor
 * do Supabase, porque `postgres` tem BYPASSRLS e as policies nem chegam a ser
 * avaliadas. Como o SQL Editor é justamente onde o script é aplicado, na
 * prática ele nunca roda. Este teste conecta como app_ecommerce (sem
 * BYPASSRLS), então aqui as policies valem de verdade.
 *
 * Usa JDBC cru via TransactionalDataAccess de propósito: CupomDAOImpl e
 * AvaliacaoDAOImpl ainda são stubs (issue futura), e a issue #9 entrega
 * apenas a camada SQL — é ela que está sob teste.
 *
 * Pré-requisitos:
 * - Arquivo .env na raiz com DB_URL, DB_USER, DB_PASSWORD
 * - Scripts 01..05 de src/main/sql aplicados
 *
 * Sem .env, ou com o script 05 ainda não aplicado, a classe é pulada.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CupomAvaliacaoRlsIntegrationTest {

    /** SQLSTATE 42501 — a policy negou a escrita. */
    private static final String INSUFFICIENT_PRIVILEGE = "42501";
    /** SQLSTATE 23514 — violação de CHECK constraint. */
    private static final String CHECK_VIOLATION = "23514";

    private static final Long ID_ADMIN = 0L; // ADMIN é liberado pelo role, não pelo id

    private static String sufixo;
    private static long clienteA;
    private static long clienteB;
    private static long vendedor;
    private static long categoria;
    private static long produto;
    private static long cupomValido;
    private static long cupomInativo;
    private static long cupomExpirado;
    private static long avaliacaoDeA;
    private static long avaliacaoDeB;

    @BeforeAll
    static void montarCenario() {
        Assumptions.assumeTrue(new File(".env").exists(),
                "Sem .env na raiz: teste de integração pulado (ver .env.example)");
        Assumptions.assumeTrue(tabelasExistem(),
                "Tabelas cupom/avaliacao ausentes: aplique src/main/sql/05_ddl_rls_cupom_avaliacao.sql");

        sufixo = String.valueOf(System.currentTimeMillis());

        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            clienteA = criarCliente("Cliente A #9", "cliente.a." + sufixo);
            clienteB = criarCliente("Cliente B #9", "cliente.b." + sufixo);

            vendedor = inserirRetornandoId(
                    "INSERT INTO usuario (nome, email, senha_hash, role, dtype)"
                            + " VALUES (?, ?, 'hash', 'VENDEDOR', 'Vendedor') RETURNING id",
                    "Vendedor #9", "vendedor." + sufixo + "@exemplo.local");
            executar("INSERT INTO vendedor (id, razao_social, cnpj_cpf) VALUES (?, ?, ?)",
                    vendedor, "Loja Issue 9 LTDA", sufixo);

            categoria = inserirRetornandoId(
                    "INSERT INTO categoria (nome, descricao) VALUES (?, 'teste issue 9') RETURNING id",
                    "Categoria Issue 9 " + sufixo);

            produto = inserirRetornandoId(
                    "INSERT INTO produto (nome, estoque, preco, id_vendedor, id_categoria)"
                            + " VALUES (?, 10, 100.00, ?, ?) RETURNING id_produto",
                    "Produto Avaliado #9", vendedor, categoria);

            cupomValido = criarCupom("ISSUE9-OK-" + sufixo, "ATIVO", 30);
            cupomInativo = criarCupom("ISSUE9-INATIVO-" + sufixo, "INATIVO", 30);
            cupomExpirado = criarCupom("ISSUE9-EXPIRADO-" + sufixo, "ATIVO", -1);
        });
    }

    @AfterAll
    static void limparCenario() {
        if (sufixo == null) {
            return; // assumption falhou: nada foi criado
        }
        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            executar("DELETE FROM avaliacao WHERE id_produto = ?", produto);
            executar("DELETE FROM cupom WHERE codigo LIKE ?", "ISSUE9-%" + sufixo);
            executar("DELETE FROM produto WHERE id_produto = ?", produto);
            executar("DELETE FROM categoria WHERE id = ?", categoria);
            executar("DELETE FROM usuario WHERE id IN (?, ?, ?)", clienteA, clienteB, vendedor);
        });
    }

    @AfterEach
    void limparContexto() {
        TenantContext.clear();
    }

    // ============================================================
    // cupom — critério de aceite 2
    // ============================================================

    @Test
    @Order(1)
    @DisplayName("ADMIN enxerga cupom ativo, inativo e expirado")
    void adminEnxergaTodosOsCupons() {
        int n = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> contar("SELECT count(*) FROM cupom WHERE id IN (?, ?, ?)",
                        cupomValido, cupomInativo, cupomExpirado));
        assertEquals(3, n, "cupom_select libera tudo para ADMIN");
    }

    @Test
    @Order(2)
    @DisplayName("CRITÉRIO DE ACEITE: cupom expirado e inativo não aparecem para o cliente")
    void clienteNaoEnxergaCupomInativoNemExpirado() {
        int inativo = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> contar("SELECT count(*) FROM cupom WHERE id = ?", cupomInativo));
        assertEquals(0, inativo, "cupom INATIVO não pode aparecer para cliente");

        int expirado = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> contar("SELECT count(*) FROM cupom WHERE id = ?", cupomExpirado));
        assertEquals(0, expirado,
                "cupom ATIVO mas com data_expiracao no passado não pode aparecer para cliente");
    }

    @Test
    @Order(3)
    @DisplayName("Cupom ATIVO e dentro da validade aparece para o cliente")
    void clienteEnxergaCupomUtilizavel() {
        int n = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> contar("SELECT count(*) FROM cupom WHERE id = ?", cupomValido));
        assertEquals(1, n, "cupom utilizável tem de aparecer, senão a policy é restritiva demais");
    }

    @Test
    @Order(4)
    @DisplayName("Sessão anônima também não enxerga cupom inativo ou expirado")
    void anonimoNaoEnxergaCupomInutilizavel() {
        int n = JwtAuthenticationFilter.executeAnonymous(
                () -> contar("SELECT count(*) FROM cupom WHERE id IN (?, ?)",
                        cupomInativo, cupomExpirado));
        assertEquals(0, n);
    }

    @Test
    @Order(5)
    @DisplayName("Escrita de cupom é exclusiva de ADMIN")
    void clienteNaoEmiteCupom() {
        assertSqlState(INSUFFICIENT_PRIVILEGE,
                () -> JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                        () -> criarCupom("ISSUE9-PIRATA-" + sufixo, "ATIVO", 30)));

        // e o UPDATE do cliente não alcança linha nenhuma (nega em silêncio)
        int linhas = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> atualizar("UPDATE cupom SET valor_desconto = 999 WHERE id = ?", cupomValido));
        assertEquals(0, linhas, "cupom_update exige ADMIN");
    }

    // ============================================================
    // avaliacao — critério de aceite 1
    // ============================================================

    @Test
    @Order(6)
    @DisplayName("Cliente assina a própria avaliação")
    void clienteAssinaPropriaAvaliacao() {
        avaliacaoDeA = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> criarAvaliacao(5, "Avaliacao do A", clienteA, produto));
        assertTrue(avaliacaoDeA > 0, "INSERT ... RETURNING deveria devolver o id");
    }

    @Test
    @Order(7)
    @DisplayName("CRITÉRIO DE ACEITE: cliente não assina avaliação com o id de outro cliente")
    void clienteNaoAssinaAvaliacaoComIdDeOutro() {
        assertSqlState(INSUFFICIENT_PRIVILEGE,
                () -> JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                        () -> criarAvaliacao(1, "Avaliacao forjada", clienteB, produto)));
    }

    @Test
    @Order(8)
    @DisplayName("Sessão anônima não cria avaliação (e o NULLIF evita 22P02)")
    void anonimoNaoCriaAvaliacao() {
        assertSqlState(INSUFFICIENT_PRIVILEGE,
                () -> JwtAuthenticationFilter.executeAnonymous(
                        () -> criarAvaliacao(5, "Anonimo", clienteA, produto)));
    }

    @Test
    @Order(9)
    @DisplayName("CHECK rejeita nota fora de 1..5")
    void notaForaDaFaixaERejeitada() {
        assertSqlState(CHECK_VIOLATION,
                () -> JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                        () -> criarAvaliacao(6, "Nota invalida", clienteA, produto)));
        assertSqlState(CHECK_VIOLATION,
                () -> JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                        () -> criarAvaliacao(0, "Nota invalida", clienteA, produto)));
    }

    @Test
    @Order(10)
    @DisplayName("Leitura de avaliação é pública")
    void avaliacaoTemLeituraPublica() {
        avaliacaoDeB = JwtAuthenticationFilter.executeAuthenticated(clienteB, "CLIENTE",
                () -> criarAvaliacao(3, "Avaliacao do B", clienteB, produto));

        int anonimo = JwtAuthenticationFilter.executeAnonymous(
                () -> contar("SELECT count(*) FROM avaliacao WHERE id IN (?, ?)",
                        avaliacaoDeA, avaliacaoDeB));
        assertEquals(2, anonimo, "avaliacao_select é USING (true)");

        int clienteVendoDoOutro = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> contar("SELECT count(*) FROM avaliacao WHERE id = ?", avaliacaoDeB));
        assertEquals(1, clienteVendoDoOutro, "A enxerga a avaliação de B");
    }

    @Test
    @Order(11)
    @DisplayName("Cliente altera a própria avaliação, mas não a de outro")
    void clienteSoAlteraAPropriaAvaliacao() {
        int propria = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> atualizar("UPDATE avaliacao SET nota = 4 WHERE id = ?", avaliacaoDeA));
        assertEquals(1, propria);

        int deOutro = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> atualizar("UPDATE avaliacao SET nota = 1 WHERE id = ?", avaliacaoDeB));
        assertEquals(0, deOutro, "RLS nega em silêncio: 0 linhas afetadas");

        int notaDeB = JwtAuthenticationFilter.executeAnonymous(
                () -> contar("SELECT nota FROM avaliacao WHERE id = ?", avaliacaoDeB));
        assertEquals(3, notaDeB, "a nota de B não pode ter mudado");
    }

    @Test
    @Order(12)
    @DisplayName("WITH CHECK impede transferir a autoria da avaliação")
    void clienteNaoTransfereAutoria() {
        assertSqlState(INSUFFICIENT_PRIVILEGE,
                () -> JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                        () -> atualizar("UPDATE avaliacao SET id_cliente = ? WHERE id = ?",
                                clienteB, avaliacaoDeA)));
    }

    @Test
    @Order(13)
    @DisplayName("Cliente não apaga avaliação de outro; o autor apaga a própria")
    void clienteSoApagaAPropriaAvaliacao() {
        int deOutro = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> atualizar("DELETE FROM avaliacao WHERE id = ?", avaliacaoDeB));
        assertEquals(0, deOutro, "avaliacao_delete só alcança as linhas do autor");

        int propria = JwtAuthenticationFilter.executeAuthenticated(clienteA, "CLIENTE",
                () -> atualizar("DELETE FROM avaliacao WHERE id = ?", avaliacaoDeA));
        assertEquals(1, propria, "o autor apaga a própria avaliação");
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static boolean tabelasExistem() {
        try {
            int n = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                    () -> contar("SELECT count(*) FROM information_schema.tables"
                            + " WHERE table_schema = 'public' AND table_name IN ('cupom', 'avaliacao')"));
            return n == 2;
        } catch (RuntimeException e) {
            return false; // sem banco alcançável, o assumeTrue do .env já cobre
        }
    }

    private static long criarCliente(String nome, String prefixoEmail) {
        long id = inserirRetornandoId(
                "INSERT INTO usuario (nome, email, senha_hash, role, dtype)"
                        + " VALUES (?, ?, 'hash', 'CLIENTE', 'Cliente') RETURNING id",
                nome, prefixoEmail + "@exemplo.local");
        executar("INSERT INTO cliente (id, telefone) VALUES (?, '(83) 90000-0000')", id);
        return id;
    }

    private static long criarCupom(String codigo, String status, int diasAteExpirar) {
        return inserirRetornandoId(
                // CAST explícito: com parâmetro não tipado o PostgreSQL não
                // resolve sozinho qual operador date + ? aplicar.
                "INSERT INTO cupom (codigo, valor_desconto, data_expiracao, status)"
                        + " VALUES (?, 10.00, CURRENT_DATE + CAST(? AS INTEGER), ?) RETURNING id",
                codigo, diasAteExpirar, status);
    }

    private static long criarAvaliacao(int nota, String comentario, long idCliente, long idProduto) {
        return inserirRetornandoId(
                "INSERT INTO avaliacao (nota, comentario, id_cliente, id_produto)"
                        + " VALUES (?, ?, ?, ?) RETURNING id",
                nota, comentario, idCliente, idProduto);
    }

    private static long inserirRetornandoId(String sql, Object... params) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                aplicarParametros(stmt, params);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
            // INSERT ... RETURNING filtrado pela policy de SELECT devolve vazio
            return 0L;
        });
    }

    private static int contar(String sql, Object... params) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                aplicarParametros(stmt, params);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    private static int atualizar(String sql, Object... params) {
        return TransactionalDataAccess.executeInTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                aplicarParametros(stmt, params);
                return stmt.executeUpdate();
            }
        });
    }

    private static void executar(String sql, Object... params) {
        atualizar(sql, params);
    }

    private static void aplicarParametros(PreparedStatement stmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    /**
     * O TransactionalDataAccess embrulha tudo em RuntimeException("Erro na
     * transação"), então o SQLSTATE fica soterrado na cadeia de causas.
     * Afirmar sobre ele (e não sobre a mensagem) é o que distingue "a policy
     * negou" de "a query estava errada".
     */
    private static void assertSqlState(String sqlStateEsperado, Executable acao) {
        Throwable erro = assertThrows(Throwable.class, acao,
                "esperava que o banco recusasse a operação");
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sqlStateEsperado.equals(sql.getSQLState())) {
                return;
            }
        }
        fail("esperava SQLSTATE " + sqlStateEsperado + ", veio: " + descreverCadeia(erro));
    }

    private static String descreverCadeia(Throwable erro) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = erro; t != null; t = t.getCause()) {
            sb.append(t.getClass().getSimpleName());
            if (t instanceof SQLException sql) {
                sb.append("(SQLSTATE=").append(sql.getSQLState()).append(")");
            }
            sb.append(": ").append(t.getMessage()).append(" | ");
        }
        return sb.toString();
    }
}
