package br.edu.ifpb.es.daw.filter;

import br.edu.ifpb.es.daw.context.TenantContext;

import java.util.function.Supplier;

/**
 * Filtro de autenticação adaptado para arquitetura sem web server.
 *
 * Em uma arquitetura web tradicional, este seria um Servlet Filter
 * que intercepta cada requisição HTTP, extrai o JWT do header
 * Authorization, valida o token e preenche o TenantContext.
 *
 * Como o projeto atual não possui camada HTTP, este filtro é
 * invocado programaticamente: o chamador fornece a identidade
 * já extraída (usuarioId + role) e a ação a executar.
 *
 * FLUXO:
 * 1. Set TenantContext com usuarioId e role
 * 2. Executar a ação (DAOs leem o contexto via TransactionalDataAccess)
 * 3. Limpar TenantContext no finally (sempre, mesmo com erro)
 *
 * REQUISIÇÕES ANÔNIMAS:
 * Usar executeAnonymous(). O TenantContext permanece null.
 * O TransactionalDataAccess detecta null e NÃO executa set_config.
 * As policies de RLS devem tratar current_setting('app.usuario_id', true)
 * retornando NULL.
 *
 * TODO (issue futura): Adicionar método que aceita token JWT string,
 * valida e extrai usuarioId + role automaticamente:
 *   public static <T> T executeWithToken(String jwtToken, Supplier<T> action) {
 *       JwtClaims claims = validateAndExtract(jwtToken);
 *       return executeAuthenticated(claims.usuarioId(), claims.role(), action);
 *   }
 */
public class JwtAuthenticationFilter {

    private JwtAuthenticationFilter() {
        // Não instanciável
    }

    /**
     * Executa uma ação dentro de um contexto autenticado.
     *
     * @param usuarioId ID do usuário extraído do JWT
     * @param role      papel do usuário (ex.: "cliente", "vendedor", "admin")
     * @param action    a lógica de negócio a executar
     * @param <T>       tipo de retorno
     * @return o resultado da ação
     */
    public static <T> T executeAuthenticated(Long usuarioId, String role, Supplier<T> action) {
        try {
            TenantContext.set(usuarioId, role);
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Versão void para operações que não retornam valor
     * (save, update, delete, deleteAll).
     */
    public static void executeAuthenticated(Long usuarioId, String role, Runnable action) {
        executeAuthenticated(usuarioId, role, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Executa uma ação sem autenticação (anônima).
     *
     * O TenantContext permanece null durante toda a execução.
     * Isso cobre casos como:
     * - POST /clientes (cadastro público)
     * - GET /produtos (listagem pública)
     *
     * @param action a lógica de negócio a executar
     * @param <T>    tipo de retorno
     * @return o resultado da ação
     */
    public static <T> T executeAnonymous(Supplier<T> action) {
        try {
            // Não chama TenantContext.set() — contexto permanece null
            return action.get();
        } finally {
            // Safety: garante que nada vaze, mesmo que alguém
            // tenha setado o contexto antes erroneamente
            TenantContext.clear();
        }
    }

    /**
     * Versão void para operações anônimas sem retorno.
     */
    public static void executeAnonymous(Runnable action) {
        executeAnonymous(() -> {
            action.run();
            return null;
        });
    }
}