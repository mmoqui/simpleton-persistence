package io.simpleton.persistence;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;

public final class GenericRepositoryFactory {

    private GenericRepositoryFactory() {
    }

    @SuppressWarnings("unchecked")
    public static <R extends Repository<?>> R create(Class<R> repositoryInterface, DataSource dataSource) {
        try {
            String implName = repositoryInterface.getPackageName() + "." + repositoryInterface.getSimpleName() + "Impl";
            Class<?> implClass = Class.forName(implName);
            Constructor<?> constructor = implClass.getConstructor(DataSource.class);
            return (R) constructor.newInstance(dataSource);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create generated repository for " + repositoryInterface.getName(), exception);
        }
    }
}
