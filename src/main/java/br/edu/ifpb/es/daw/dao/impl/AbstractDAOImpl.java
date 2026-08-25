package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.DAO;

/**
 * Classe base abstrata para DAOs JDBC.
 *
 * ⚠️ CONTRATO OBRIGATÓRIO:
 * NENHUM DAO deve abrir Connection diretamente do DatabaseConnection.
 * Todo acesso a dados DEVE passar por TransactionalDataAccess.
 *
 * Exemplo de uso correto:
 *   TransactionalDataAccess.executeInTransactionVoid(conn -> {
 *       try (PreparedStatement stmt = conn.prepareStatement(sql)) {
 *           // setar parâmetros...
 *           stmt.executeUpdate();
 *       }
 *   });
 *
 * Isso garante que o contexto RLS (app.usuario_id, app.usuario_role)
 * seja propagado antes de qualquer SQL.
 */
public abstract class AbstractDAOImpl<T> implements DAO<T> {
    // Sem getConnection() — DAOs usam TransactionalDataAccess
}