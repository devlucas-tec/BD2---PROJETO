package br.edu.ifpb.es.daw.util;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {

    private static final Dotenv dotenv = Dotenv.load();

    private DatabaseConnection() {
        // Classe utilitária — não instanciável
    }

    public static Connection getConnection() throws SQLException {
        String url = dotenv.get("DB_URL");
        String user = dotenv.get("DB_USER");
        String password = dotenv.get("DB_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new IllegalStateException(
                    "Variáveis DB_URL, DB_USER e DB_PASSWORD não encontradas.\n" +
                            "Crie um arquivo .env na raiz do projeto (use .env.example como template)."
            );
        }

        // Garantir que prepareThreshold=0 esteja presente na URL.
        // Necessário porque o Supabase usa PgBouncer em transaction mode (porta 6543),
        // que não suporta server-side prepared statements do driver JDBC.
        // Sem isso, ocorre erro "prepared statement S_1 already exists".
        if (!url.contains("prepareThreshold=")) {
            url += (url.contains("?") ? "&" : "?") + "prepareThreshold=0";
        }

        return DriverManager.getConnection(url, user, password);
    }
}