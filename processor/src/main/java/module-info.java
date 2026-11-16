/**
 * JDBC annotation processor framework module.
 *
 * <h2>Exported public API</h2>
 * <ul>
 *   <li>{@code io.simpleton.persistence.annotation} — {@code @JdbcRepository},
 *       {@code Repository<T,ID>}, {@code RepositoryFactory<R>},
 *       {@code ColumnDescriptor}</li>
 *   <li>{@code io.simpleton.persistence.mapper} — {@code EntityMapper<T>}</li>
 * </ul>
 *
 * <h2>Usage in a modular project</h2>
 * The consuming module must:
 * <ol>
 *   <li>Declare {@code requires io.simpleton.persistence;}</li>
 *   <li>Open the entity package to the framework so that
 *       {@code MethodHandles.privateLookupIn()} can access private fields:
 *       {@code opens com.example.entity to io.simpleton.persistence;}</li>
 *   <li>Open the generated repository package for the same reason:
 *       {@code opens com.example.repository to io.simpleton.persistence;}</li>
 * </ol>
 *
 * <h2>Annotation processor registration</h2>
 * The {@code javax.annotation.processing.Processor} service is declared via
 * {@code provides} so that {@code javac} discovers the processor automatically
 * in modular projects, without extra configuration.
 */
module io.simpleton.persistence {

    // Public API
    exports io.simpleton.persistence.annotation;
    exports io.simpleton.persistence.mapper;

    // JDBC API (DataSource, Connection, PreparedStatement, ResultSet, etc.)
    requires java.sql;

    // Compiler mirror API, required by the annotation processor
    requires java.compiler;

    // JavaPoet — compile-time only (not required by consumers)
    requires static com.squareup.javapoet;

    // Google Auto-Service — generates the SPI service file at processor compile time
    requires static com.google.auto.service;

    // Register the annotation processor as a Java SPI service
    provides javax.annotation.processing.Processor
            with io.simpleton.persistence.processor.JdbcRepositoryProcessor;
}
