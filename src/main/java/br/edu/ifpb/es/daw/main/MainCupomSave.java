package br.edu.ifpb.es.daw.main;

import br.edu.ifpb.es.daw.dao.CupomDAO;
import br.edu.ifpb.es.daw.dao.impl.CupomDAOImpl;
import br.edu.ifpb.es.daw.entities.Cupom;
import br.edu.ifpb.es.daw.entities.StatusCupom;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Critério de aceite da issue #10, lado cupom.
 *
 * O projeto não tem camada HTTP (a issue #2 removeu Spring/JPA), então cada
 * bloco abaixo é a requisição que um controller faria, executada pelo
 * JwtAuthenticationFilter — que é quem preenche o TenantContext e portanto
 * define o que o RLS permite.
 *
 * O ponto da demonstração: a MESMA chamada de DAO devolve resultados
 * diferentes conforme quem está autenticado, e a validade do cupom NÃO pode
 * depender disso. Ao final, remove tudo que criou.
 *
 * Requer .env configurado e os scripts 01..05 aplicados.
 */
public class MainCupomSave {

    private static final Long ID_ADMIN = 0L; // ADMIN é liberado pelo role, não pelo id
    private static final Long ID_CLIENTE = 1L;

    public static void main(String[] args) {
        CupomDAO cupomDAO = new CupomDAOImpl();

        String sufixo = String.valueOf(System.currentTimeMillis());
        Cupom valido = novoCupom("OK-" + sufixo, StatusCupom.ATIVO, 30);
        Cupom inativo = novoCupom("INATIVO-" + sufixo, StatusCupom.INATIVO, 30);
        Cupom expirado = novoCupom("EXPIRADO-" + sufixo, StatusCupom.ATIVO, -1);

        // ---------- POST /cupons (ADMIN) ----------
        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
            cupomDAO.save(valido);
            cupomDAO.save(inativo);
            cupomDAO.save(expirado);
        });
        System.out.println("POST /cupons -> ids " + valido.getId() + ", "
                + inativo.getId() + ", " + expirado.getId());

        try {
            // ---------- POST /cupons como CLIENTE: o RLS recusa ----------
            try {
                Cupom pirata = novoCupom("PIRATA-" + sufixo, StatusCupom.ATIVO, 30);
                JwtAuthenticationFilter.executeAuthenticated(ID_CLIENTE, "CLIENTE",
                        () -> cupomDAO.save(pirata));
                System.out.println("POST /cupons como CLIENTE -> NAO DEVERIA CHEGAR AQUI");
            } catch (RuntimeException e) {
                System.out.println("POST /cupons como CLIENTE -> recusado pelo RLS (esperado)");
            }

            // ---------- GET /cupons (ADMIN vê tudo) ----------
            int totalAdmin = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                    () -> cupomDAO.findAll().size());
            System.out.println("GET /cupons (ADMIN)   -> " + totalAdmin + " cupom(ns)");

            // ---------- GET /cupons (CLIENTE só vê utilizável) ----------
            int totalCliente = JwtAuthenticationFilter.executeAuthenticated(ID_CLIENTE, "CLIENTE",
                    () -> cupomDAO.findAll().size());
            System.out.println("GET /cupons (CLIENTE) -> " + totalCliente
                    + " cupom(ns) (RLS esconde inativo e expirado)");

            // ---------- GET /cupons/{codigo} — findByCodigo ----------
            System.out.println();
            System.out.println("--- findByCodigo: o resultado depende do papel ---");
            imprimirBusca(cupomDAO, ID_CLIENTE, "CLIENTE", expirado.getCodigo());
            imprimirBusca(cupomDAO, ID_ADMIN, "ADMIN", expirado.getCodigo());

            // ---------- Validação de expiração: independente do papel ----------
            System.out.println();
            System.out.println("--- findValidoByCodigo: mesma resposta para todos ---");
            Cupom validoParaAdmin = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                    () -> cupomDAO.findValidoByCodigo(expirado.getCodigo()));
            System.out.println("findValidoByCodigo(expirado) como ADMIN   -> " + validoParaAdmin
                    + " (esperado: null — o ADMIN enxerga, mas nao pode APLICAR)");

            Cupom validoOk = JwtAuthenticationFilter.executeAuthenticated(ID_CLIENTE, "CLIENTE",
                    () -> cupomDAO.findValidoByCodigo(valido.getCodigo()));
            System.out.println("findValidoByCodigo(valido) como CLIENTE   -> " + validoOk);

            // ---------- isExpirado usa a data do banco ----------
            boolean expirouMesmo = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                    () -> cupomDAO.isExpirado(expirado));
            boolean validoExpirou = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                    () -> cupomDAO.isExpirado(valido));
            System.out.println("isExpirado(expirado) -> " + expirouMesmo + " (esperado: true)");
            System.out.println("isExpirado(valido)   -> " + validoExpirou + " (esperado: false)");

            // ---------- findValidos ----------
            int validosCliente = JwtAuthenticationFilter.executeAuthenticated(ID_CLIENTE, "CLIENTE",
                    () -> cupomDAO.findValidos().size());
            int validosAdmin = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                    () -> cupomDAO.findValidos().size());
            System.out.println();
            System.out.println("findValidos (CLIENTE) -> " + validosCliente);
            System.out.println("findValidos (ADMIN)   -> " + validosAdmin
                    + " (igual: o predicado esta no SQL, nao na policy)");

            // ---------- PUT /cupons/{id} (ADMIN desativa o cupom) ----------
            valido.setStatus(StatusCupom.INATIVO);
            JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                    () -> cupomDAO.update(valido));
            Cupom sumiu = JwtAuthenticationFilter.executeAuthenticated(ID_CLIENTE, "CLIENTE",
                    () -> cupomDAO.findByCodigo(valido.getCodigo()));
            System.out.println();
            System.out.println("PUT /cupons/" + valido.getId() + " status=INATIVO"
                    + " -> visao do CLIENTE: " + sumiu + " (esperado: null)");

        } finally {
            JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> {
                cupomDAO.delete(valido);
                cupomDAO.delete(inativo);
                cupomDAO.delete(expirado);
            });
            System.out.println("cupons de teste removidos");
        }
    }

    private static void imprimirBusca(CupomDAO dao, Long usuarioId, String role, String codigo) {
        Cupom achado = JwtAuthenticationFilter.executeAuthenticated(usuarioId, role,
                () -> dao.findByCodigo(codigo));
        System.out.println("findByCodigo(expirado) como " + role + " -> " + achado);
    }

    private static Cupom novoCupom(String codigo, StatusCupom status, int diasAteExpirar) {
        Cupom c = new Cupom();
        c.setCodigo(codigo);
        c.setValorDesconto(new BigDecimal("10.00"));
        c.setDataExpiracao(LocalDate.now().plusDays(diasAteExpirar));
        c.setStatus(status);
        return c;
    }
}
