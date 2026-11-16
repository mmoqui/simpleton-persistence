package io.github.jdbcprocessor.annotation;

import io.github.jdbcprocessor.mapper.EntityMapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as a JDBC-backed repository.
 *
 * <p>The annotation processor generates two classes at compile time:</p>
 * <ul>
 *   <li>{@code XxxImpl} — the JDBC implementation with SQL statements
 *       pre-built in the constructor;</li>
 *   <li>{@code XxxFactory} — the factory that instantiates the impl with
 *       the right mapper.</li>
 * </ul>
 *
 * <h2>Case 1 — convention (field names match column names, PK named {@code id})</h2>
 * <pre>{@code
 * @JdbcRepository("users")
 * public interface UserRepository extends Repository<User, Long> {}
 * }</pre>
 *
 * <h2>Case 2 — custom mapper (names differ)</h2>
 * <pre>{@code
 * @JdbcRepository(table = "users", mapper = UserMapper.class)
 * public interface UserRepository extends Repository<User, Long> {}
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface JdbcRepository {

    /**
     * SQL table name.
     * Defaults to the simple name of the entity (first generic parameter of
     * {@link Repository}) when left blank.
     */
    String table() default "";

    /**
     * Custom mapper class to use.
     * Must implement {@link EntityMapper}{@code <T>}.
     * When omitted ({@link Void}{@code .class} default), the processor generates
     * a default mapper from the {@code private} non-{@code transient} fields
     * of the entity, using {@code VarHandle} for field access.
     */
    Class<?> mapper() default Void.class;
}
