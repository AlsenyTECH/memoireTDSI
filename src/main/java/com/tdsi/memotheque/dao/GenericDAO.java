package com.tdsi.memotheque.dao;

import java.sql.SQLException;
import java.util.List;

/** Contrat CRUD commun à tous les DAO. [M1] contrat gelé */
public interface GenericDAO<T> {
    void insert(T obj) throws SQLException;
    void update(T obj) throws SQLException;
    void delete(int id) throws SQLException;
    T findById(int id) throws SQLException;
    List<T> findAll() throws SQLException;
}
