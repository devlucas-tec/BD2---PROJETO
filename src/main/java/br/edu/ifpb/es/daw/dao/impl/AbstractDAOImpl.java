package br.edu.ifpb.es.daw.dao.impl;

import br.edu.ifpb.es.daw.dao.DAO;
import br.edu.ifpb.es.daw.util.DatabaseConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Classe base abstrata para DAOs JDBC.
 * Fornece o acesso à conexão com o banco.
 * Cada DAO concreto implementa seus próprios métodos CRUD com SQL específico.
 */
public abstract class AbstractDAOImpl<T> implements DAO<T> {

	protected Connection getConnection() throws SQLException {
		return DatabaseConnection.getConnection();
	}
}