package br.edu.ifpb.es.daw.context;

/**
 * Contexto de tenant baseado em ThreadLocal.
 *
 * Armazena usuarioId e role da requisição atual.
 * Deve ser preenchido pelo JwtAuthenticationFilter
 * e limpo no finally do mesmo filtro.
 *
 * Para requisições anônimas (sem JWT), o contexto
 * permanece null — o TransactionalDataAccess detecta
 * isso e não executa set_config.
 */
public class TenantContext {

    private TenantContext() {
        // Não instanciável
    }

    /**
     * Registro imutável com a identidade do usuário atual.
     */
    public record TenantInfo(Long usuarioId, String role) {}

    private static final ThreadLocal<TenantInfo> CONTEXT = new ThreadLocal<>();

    /**
     * Define o contexto do usuário autenticado.
     * Deve ser chamado APÓS a validação do token JWT.
     */
    public static void set(Long usuarioId, String role) {
        CONTEXT.set(new TenantInfo(usuarioId, role));
    }

    /**
     * Retorna o contexto atual, ou null se anônimo.
     */
    public static TenantInfo get() {
        return CONTEXT.get();
    }

    /**
     * Limpa o contexto. OBRIGATÓRIO no finally do filtro.
     * Usa remove() (não set(null)) para evitar memory leak
     * em servidores com thread pools.
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * Verifica se há um usuário autenticado no contexto.
     */
    public static boolean isAuthenticated() {
        return CONTEXT.get() != null;
    }
}