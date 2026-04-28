package io.simpleton.persistence;

import java.util.List;
import java.util.Optional;

public interface Repository<T> {
    Optional<T> getById(Object id);

    List<T> findAll();

    T save(T entity);

    T update(T entity);

    boolean delete(Object id);
}
