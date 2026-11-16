package io.github.jdbcprocessor.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a custom SQL query on a repository interface method.
 *
 * <h2>Query syntax</h2>
 * <p>The query string is standard SQL with two conveniences:</p>
 * <ul>
 *   <li><b>Short-form SELECT</b> — instead of listing all columns, write
 *       {@code SELECT alias FROM table alias}. The processor rewrites it to
 *       {@code SELECT * FROM table alias} at compile time:
 *       <pre>{@code "SELECT u FROM users u WHERE u.age > :minAge"}</pre></li>
 *   <li><b>Named parameters</b> — prefix a name with {@code :} to reference a
 *       method parameter. Parameters are matched by their position of appearance
 *       in the query string against the method parameter list (left to right):
 *       <pre>{@code "SELECT u FROM users u WHERE u.username = :username AND u.age > :minAge"}</pre>
 *   </li>
 * </ul>
 *
 * <h2>Supported return types</h2>
 * <table>
 *   <tr><th>Return type</th><th>Behaviour</th></tr>
 *   <tr><td>{@code List<T>}</td><td>All matching rows are returned.</td></tr>
 *   <tr><td>{@code Set<T>}</td><td>All matching rows, deduplicated.</td></tr>
 *   <tr><td>{@code Optional<T>}</td><td>At most one row; empty if none.</td></tr>
 *   <tr><td>{@code T}</td><td>Exactly one row; throws
 *       {@link io.github.jdbcprocessor.annotation.NotFoundException}
 *       if no row matches.</td></tr>
 * </table>
 *
 * <h2>Usage example</h2>
 * <pre>{@code
 * @JdbcRepository(table = "users")
 * public interface UserRepository extends Repository<User, Long> {
 *
 *     @Query("SELECT u FROM users u WHERE u.username = :username")
 *     Optional<User> findByUsername(String username);
 *
 *     @Query("SELECT u FROM users u WHERE u.age > :minAge")
 *     List<User> findOlderThan(int minAge);
 *
 *     @Query("SELECT u FROM users u WHERE u.email = :email")
 *     User findByEmail(String email);  // throws NotFoundException if absent
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Query {

    /**
     * The SQL query to execute.
     * <p>May use the short-form {@code SELECT alias FROM table alias} syntax
     * and named parameters prefixed with {@code :}.</p>
     */
    String value();
}
