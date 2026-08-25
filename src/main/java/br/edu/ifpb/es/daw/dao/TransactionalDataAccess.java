package br.edu.ifpb.es.daw.dao;

import br.edu.ifpb.es.daw.context.TenantContext;
import br.edu.ifpb.es.daw.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Componente central de acesso a dados com propagação de RLS.
 *
 * ⚠️ CONTRATO OBRIGATÓRIO:
 * NENHUM DAO deve abrir Connection diretamente do DatabaseConnection.
 * Todo acesso a dados DEVE passar por esta classe.
 *
 * O fluxo interno de executeInTransaction() é:
 * 1. Abrir conexão (DriverManager)
 * 2. setAutoCommit(false) — inicia transação explícita
 * 3. SET LOCAL app.usuario_id e app.usuario_role (se autenticado)
 * 4. Executar a lógica do DAO (callback)
 * 5. Commit em sucesso / Rollback em erro
 * 6. Fechar conexão (restaurando autoCommit antes)
 *
 * O uso de set_config(..., true) é OBRIGATÓRIO:
 * true = escopo LOCAL, atrelado à transação atual.
 * O setting nasce e morre dentro da mesma transação.
 * Isso é necessário porque o Supabase usa PgBouncer em
 * transaction mode (porta 6543), que reatribui conexões
 * entre transações — settings de sessão (false) se perderiam.
 */
public class TransactionalDataAccess {

    private TransactionalDataAccess() {
        // Não instanciável
    }

    // SQL para setar variáveis de sessão no escopo LOCAL (por transação)
    private static final String SET_USUARIO_ID =
            "SELECT set_config('app.usuario_id', ?, true)";
    private static final String SET_USUARIO_ROLE =
            "SELECT set_config('app.usuario_role', ?, true)";

    /**
     * Executa uma ação dentro de uma transação com contexto RLS.
     *
     * @param action a lógica do DAO a ser executada
     * @param <T>    tipo de retorno
     * @return o resultado da ação
     */
    public static <T> T executeInTransaction(ConnectionCallback<T> action) {
        Connection conn = null;
        try {
            // 1. Abrir conexão
            conn = DatabaseConnection.getConnection();

            // 2. Iniciar transação explícita
            conn.setAutoCommit(false);

            // 3. Propagar contexto RLS (se autenticado)
            setRlsContext(conn);

            // 4. Executar lógica do DAO
            T result = action.doInConnection(conn);

            // 5. Commit
            conn.commit();
            return result;

        } catch (Exception e) {
            // 5. Rollback em caso de erro
            rollbackQuietly(conn);
            throw new RuntimeException("Erro na transação", e);
        } finally {
            // 6. Fechar conexão
            closeQuietly(conn);
        }
    }

    /**
     * Versão void para operações que não retornam valor
     * (save, update, delete, deleteAll).
     */
    public static void executeInTransactionVoid(ConnectionAction action) {
        executeInTransaction(conn -> {
            action.execute(conn);
            return null;
        });
    }

    /**
     * Propaga o contexto do TenantContext para variáveis de sessão do Postgres.
     *
     * Usa set_config(..., true) = escopo LOCAL, atrelado à transação atual.
     *
     * Se o contexto for anônimo (null), NÃO seta nada — as policies de RLS
     * devem tratar current_setting('app.usuario_id', true) retornando NULL.
     */
    private static void setRlsContext(Connection conn) throws SQLException {
        TenantContext.TenantInfo info = TenantContext.get();
        if (info == null) {
            // Requisição anônima — contexto vazio
            return;
        }

        // set_config('app.usuario_id', valor, true)
        // true = LOCAL: o setting morre junto com a transação (commit/rollback)
        try (PreparedStatement stmt = conn.prepareStatement(SET_USUARIO_ID)) {
            stmt.setString(1, String.valueOf(info.usuarioId()));
            stmt.executeQuery();
        }

        try (PreparedStatement stmt = conn.prepareStatement(SET_USUARIO_ROLE)) {
            stmt.setString(1, info.role());
            stmt.executeQuery();
        }
    }

    private static void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ignored) {
                // Logar em produção
            }
        }
    }

    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                // Restaurar autoCommit antes de fechar
                // (boas práticas mesmo com DriverManager)
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException ignored) {
                // Logar em produção
            }
        }
    }

    // --- Interfaces funcionais ---

    @FunctionalInterface
    public interface ConnectionCallback<T> {
        T doInConnection(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    public interface ConnectionAction {
        void execute(Connection conn) throws SQLException;
    }
}