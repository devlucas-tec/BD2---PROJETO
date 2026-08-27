package br.edu.ifpb.es.daw.main;

import br.edu.ifpb.es.daw.dao.CupomDAO;
import br.edu.ifpb.es.daw.dao.impl.CupomDAOImpl;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;

/**
 * DELETE /cupons (issue #10).
 *
 * cupom_delete exige app.usuario_role = ADMIN. Rodado sem contexto, ou como
 * CLIENTE/VENDEDOR, o comando não dá erro: o RLS filtra as linhas e o DELETE
 * afeta 0. Por isso o main assume explicitamente o papel de ADMIN.
 */
public class MainCupomDeleteAll {

    private static final Long ID_ADMIN = 0L;

    public static void main(String[] args) {
        CupomDAO cupomDAO = new CupomDAOImpl();

        int antes = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> cupomDAO.findAll().size());
        System.out.println("Cupons antes: " + antes);

        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", cupomDAO::deleteAll);

        int depois = JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN",
                () -> cupomDAO.findAll().size());
        System.out.println("Cupons depois: " + depois);
    }
}
