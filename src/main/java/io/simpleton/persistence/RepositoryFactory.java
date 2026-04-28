package io.simpleton.persistence;

import javax.sql.DataSource;

public interface RepositoryFactory<R extends Repository<?>> {
    R create(DataSource dataSource);
}
