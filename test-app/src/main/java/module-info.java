/**
 * Test and example module for the JDBC annotation processor framework.
 *
 * <h2>Two use cases illustrated</h2>
 * <ul>
 *   <li><b>Convention</b>
 *       ({@link io.github.jdbcprocessor.test.repository.UserRepository}) —
 *       Java field names match SQL column names exactly; no mapper is supplied.
 *       The processor generates a {@code DefaultEntityMapper} that accesses
 *       {@link io.github.jdbcprocessor.test.entity.User} fields via
 *       {@code VarHandle}.</li>
 *   <li><b>Custom mapper</b>
 *       ({@link io.github.jdbcprocessor.test.repository.ProductRepository}) —
 *       names differ; {@link io.github.jdbcprocessor.test.mapper.ProductMapper}
 *       is declared explicitly via
 *       {@code @JdbcRepository(mapper = ProductMapper.class)}.</li>
 * </ul>
 *
 * <h2>Required opens for DefaultEntityMapper</h2>
 * The framework uses {@code MethodHandles.privateLookupIn()} to obtain
 * {@code VarHandle} instances for {@code private} entity fields.
 * Any package containing entities managed by a default mapper
 * <em>must</em> be opened to {@code io.github.jdbcprocessor}.
 *
 * <p>The {@code repository} package is also opened because the generated
 * classes ({@code XxxImpl}, {@code XxxFactory}) reside there and
 * {@code DefaultEntityMapper} is defined as an inner class of the factory.</p>
 */
module io.github.jdbcprocessor.test {

    requires io.github.jdbcprocessor;
    requires java.sql;

    // Opens required for MethodHandles.privateLookupIn()
    //
    // Without these directives the static VarHandle block in DefaultEntityMapper
    // would throw an IllegalAccessException at class initialisation time.

    /** Opens the entity package so the framework can access private fields of User and Product. */
    opens io.github.jdbcprocessor.test.entity to io.github.jdbcprocessor;

    /** Opens the repository package for the DefaultEntityMapper inner class. */
    opens io.github.jdbcprocessor.test.repository to io.github.jdbcprocessor;
}
