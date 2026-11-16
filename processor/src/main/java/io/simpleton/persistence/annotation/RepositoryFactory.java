package io.simpleton.persistence.annotation;

/**
 * Contract for a generated JDBC repository factory.
 *
 * <p>The factory receives a {@link javax.sql.DataSource} at construction time
 * and produces repository instances via {@link #create()}. The developer never
 * needs to know the concrete implementation class ({@code XxxImpl}).</p>
 *
 * <pre>{@code
 * UserRepositoryFactory factory = new UserRepositoryFactory(dataSource);
 * UserRepository repo = factory.create();
 * }</pre>
 *
 * @param <R> the type of the repository interface produced by this factory
 */
public interface RepositoryFactory<R> {

  /**
   * Creates a new repository instance.
   * Implementations are stateless beyond the {@code DataSource}: each call
   * is lightweight and may be invoked per request if needed.
   */
  R create();
}
