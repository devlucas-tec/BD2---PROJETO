package br.edu.ifpb.es.daw.main;

import br.edu.ifpb.es.daw.dao.AvaliacaoDAO;
import br.edu.ifpb.es.daw.dao.impl.AvaliacaoDAOImpl;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;

/**
 * DELETE /avaliacoes (issue #10).
 *
 * avaliacao_delete alcança só as linhas do autor (ou todas, se ADMIN).
 * Rodado como CLIENTE, este deleteAll limpa apenas as próprias avaliações —
 * e é assim que deve ser: o "DELETE FROM avaliacao" do DAO já sai filtrado
 * pelo RLS, sem WHERE nenhum na aplicação.
 *
 * Altere ID_USUARIO/ROLE para ver a diferença entre limpar tudo (ADMIN) e
 * limpar só as próprias (CLIENTE).
 */
public class MainAvaliacaoDeleteAll {

    private static final Long ID_USUARIO = 0L;
    private static final String ROLE = "ADMIN";

    public static void main(String[] args) {
        AvaliacaoDAO avaliacaoDAO = new AvaliacaoDAOImpl();

        // A leitura de avaliacao e publica, entao o total antes/depois pode
        // ser conferido de qualquer contexto.
        int antes = JwtAuthenticationFilter.executeAnonymous(() -> avaliacaoDAO.findAll().size());
        System.out.println("Avaliacoes antes: " + antes);

        JwtAuthenticationFilter.executeAuthenticated(ID_USUARIO, ROLE, avaliacaoDAO::deleteAll);

        int depois = JwtAuthenticationFilter.executeAnonymous(() -> avaliacaoDAO.findAll().size());
        System.out.println("Avaliacoes depois (" + ROLE + "): " + depois);
    }
}
