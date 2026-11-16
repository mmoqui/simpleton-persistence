package io.simpleton.persistence.annotation;

import java.util.List;
import java.util.Optional;

/**
 * Generic CRUD contract for a repository managing entities of type {@code T}
 * identified by a primary key of type {@code ID}.
 *
 * @param <T>  the entity type managed by this repository
 * @param <ID> the type of the primary key
 */
public interface Repository<T, ID> {

    /** Finds an entity by its primary key. */
    Optional<T> findById(ID id);

    /** Returns all entities from the table. */
    List<T> findAll();

    /**
     * Persists a new entity.
     * If the key is auto-generated, it is set on the returned object.
     */
    T save(T entity);

    /** Updates an existing entity. */
    T update(T entity);

    /** Deletes the entity identified by {@code id}. */
    void deleteById(ID id);

    /** Returns the total number of rows in the table. */
    long count();
}
