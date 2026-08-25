package br.edu.ifpb.es.daw;

import br.edu.ifpb.es.daw.context.TenantContext;
import br.edu.ifpb.es.daw.dao.TransactionalDataAccess;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração da issue #3.
 *
 * Critério de aceite: dentro de uma transação da aplicação,
 * executa SELECT current_setting('app.usuario_id', true)
 * e recebe o id do usuário do JWT enviado na requisição.
 *
 * Pré-requisitos:
 * - Arquivo .env na raiz com DB_URL, DB_USER, DB_PASSWORD
 * - Conexão com Supabase funcionando (porta 6543)
 * - Role app_ecommerce configurada
 */
class RlsContextIntegrationTest {

    @AfterEach
    void cleanup() {
        // Safety: garante que o TenantContext está limpo após cada teste
        TenantContext.clear();
    }

    @Test
    @DisplayName("current_setting('app.usuario_id') retorna o ID do usuário autenticado")
    void shouldPropagateUsuarioIdToPostgresSession() {
        Long expectedUsuarioId = 42L;
        String expectedRole = "cliente";

        // Simula uma requisição autenticada com JWT
        // (no futuro, o JwtAuthenticationFilter extrairá isso do token)
        String result = JwtAuthenticationFilter.executeAuthenticated(
                expectedUsuarioId,
                expectedRole,
                () -> {
                    // Dentro do filtro, o TenantContext está setado.
                    // O DAO (via TransactionalDataAccess) vai:
                    // 1. Abrir transação
                    // 2. set_config('app.usuario_id', '42', true) — LOCAL
                    // 3. Executar nossa query
                    // 4. Commit
                    return TransactionalDataAccess.executeInTransaction(conn -> {
                        return queryCurrentSetting(conn, "app.usuario_id");
                    });
                }
        );

        assertEquals(String.valueOf(expectedUsuarioId), result,
                "current_setting('app.usuario_id', true) deve retornar o ID do usuário");
    }

    @Test
    @DisplayName("current_setting('app.usuario_role') retorna a role do usuário autenticado")
    void shouldPropagateUsuarioRoleToPostgresSession() {
        String result = JwtAuthenticationFilter.executeAuthenticated(
                99L,
                "vendedor",
                () -> TransactionalDataAccess.executeInTransaction(conn ->
                        queryCurrentSetting(conn, "app.usuario_role")
                )
        );

        assertEquals("vendedor", result,
                "current_setting('app.usuario_role', true) deve retornar a role");
    }

    @Test
    @DisplayName("Requisição anônima: current_setting retorna NULL")
    void shouldReturnNullForAnonymousRequest() {
        String result = JwtAuthenticationFilter.executeAnonymous(
                () -> TransactionalDataAccess.executeInTransaction(conn ->
                        queryCurrentSetting(conn, "app.usuario_id")
                )
        );

        assertNull(result,
                "Para requisições anônimas, current_setting deve retornar NULL");
    }

    @Test
    @DisplayName("Contexto não vaza entre operações (escopo LOCAL)")
    void shouldNotLeakContextBetweenTransactions() {
        // Primeira operação: autenticada
        JwtAuthenticationFilter.executeAuthenticated(77L, "admin", () ->
                TransactionalDataAccess.executeInTransaction(conn -> {
                    String val = queryCurrentSetting(conn, "app.usuario_id");
                    assertEquals("77", val);
                    return null;
                })
        );

        // Segunda operação: anônima — NÃO deve ver o valor da primeira
        JwtAuthenticationFilter.executeAnonymous(
                () -> TransactionalDataAccess.executeInTransaction(conn -> {
                    String val = queryCurrentSetting(conn, "app.usuario_id");
                    assertNull(val, "Setting LOCAL não deve vazar entre transações");
                    return null;
                })
        );
    }

    /**
     * Helper: executa SELECT NULLIF(current_setting(?, true), '')
     * e retorna o valor como String (ou null se não existir / for vazio).
     *
     * O NULLIF é necessário porque o Supabase/PgBouncer pode retornar
     * string vazia ao invés de NULL quando o setting não existe.
     * String vazia quebraria o cast ::bigint nas policies de RLS.
     */
    private String queryCurrentSetting(Connection conn, String settingName) {
        String sql = "SELECT NULLIF(current_setting(?, true), '')";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, settingName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1); // pode ser null
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Erro ao consultar current_setting: " + settingName, e);
        }

        return null;
    }
}