package br.edu.ifpb.es.daw.main;

import br.edu.ifpb.es.daw.dao.CategoriaDAO;
import br.edu.ifpb.es.daw.dao.impl.CategoriaDAOImpl;
import br.edu.ifpb.es.daw.entities.Categoria;
import br.edu.ifpb.es.daw.filter.JwtAuthenticationFilter;

/**
 * Critério de aceite da issue #8, lado /categorias.
 *
 * O projeto não tem camada HTTP (a issue foi escrita antes da migração da
 * issue #2, que removeu Spring/JPA). O equivalente a "o endpoint responde"
 * aqui é este main: cada bloco abaixo é a requisição que o controller faria,
 * executada pelo JwtAuthenticationFilter — que é quem preenche o
 * TenantContext e, portanto, quem determina o que o RLS vai permitir.
 *
 * Requer .env configurado (ver .env.example) e os scripts 01..04 aplicados.
 */
public class MainCategoriaSave {

    private static final Long ID_ADMIN = 0L; // ADMIN é liberado pelo role, não pelo id

    public static void main(String[] args) {
        CategoriaDAO categoriaDAO = new CategoriaDAOImpl();

        // ---------- POST /categorias (ADMIN) ----------
        Categoria categoria = new Categoria();
        categoria.setNome("Eletronicos Issue 8");
        categoria.setDescricao("Categoria criada pelo MainCategoriaSave");

        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> categoriaDAO.save(categoria));
        System.out.println("POST /categorias -> id " + categoria.getId());

        // ---------- GET /categorias/{id} (anônimo: vitrine é pública) ----------
        Categoria porId = JwtAuthenticationFilter.executeAnonymous(() -> categoriaDAO.findById(categoria.getId()));
        System.out.println("GET /categorias/" + categoria.getId() + " (anonimo) -> " + porId);

        // ---------- GET /categorias?nome=... (findByNome, issue #8) ----------
        Categoria porNome = JwtAuthenticationFilter.executeAnonymous(
                () -> categoriaDAO.findByNome("Eletronicos Issue 8"));
        System.out.println("GET /categorias?nome=Eletronicos Issue 8 -> " + porNome);

        Categoria inexistente = JwtAuthenticationFilter.executeAnonymous(
                () -> categoriaDAO.findByNome("Nao existe"));
        System.out.println("GET /categorias?nome=Nao existe -> " + inexistente + " (esperado: null)");

        // ---------- GET /categorias ----------
        int total = JwtAuthenticationFilter.executeAnonymous(() -> categoriaDAO.findAll().size());
        System.out.println("GET /categorias -> " + total + " categoria(s)");

        // ---------- PUT /categorias/{id} (ADMIN) ----------
        porNome.setDescricao("Descricao atualizada pelo MainCategoriaSave");
        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> categoriaDAO.update(porNome));
        System.out.println("PUT /categorias/" + porNome.getId() + " -> "
                + JwtAuthenticationFilter.executeAnonymous(() -> categoriaDAO.findById(porNome.getId())).getDescricao());

        // ---------- PUT /categorias/{id} sem ser ADMIN: o RLS nega em silêncio ----------
        // categoria_update exige app.usuario_role = ADMIN. Para um VENDEDOR o
        // UPDATE alcança 0 linhas — não estoura erro, simplesmente não altera.
        porNome.setDescricao("Alteracao pirata");
        JwtAuthenticationFilter.executeAuthenticated(1L, "VENDEDOR", () -> categoriaDAO.update(porNome));
        Categoria depois = JwtAuthenticationFilter.executeAnonymous(() -> categoriaDAO.findById(porNome.getId()));
        System.out.println("PUT /categorias/" + porNome.getId() + " como VENDEDOR -> descricao continua: "
                + depois.getDescricao());

        // ---------- DELETE /categorias/{id} (ADMIN) ----------
        JwtAuthenticationFilter.executeAuthenticated(ID_ADMIN, "ADMIN", () -> categoriaDAO.delete(depois));
        System.out.println("DELETE /categorias/" + depois.getId() + " -> "
                + JwtAuthenticationFilter.executeAnonymous(() -> categoriaDAO.findById(depois.getId()))
                + " (esperado: null)");
    }
}
