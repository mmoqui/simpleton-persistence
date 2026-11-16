package io.simpleton.persistence.mapper;

import io.simpleton.persistence.annotation.ColumnDescriptor;
import io.simpleton.persistence.annotation.FieldValue;

import java.util.List;
import java.util.Map;

/**
 * Technology-neutral mapping contract between a Java entity and its
 * persistent representation.
 *
 * <p>The mapper knows only about the <em>domain</em>: which fields exist,
 * how to build an entity from a plain key/value row, and which values to
 * persist. It has no knowledge of JDBC, MongoDB, or any other storage
 * technology. The persistence back-end (e.g. a generated {@code XxxImpl})
 * is responsible for bridging between this contract and its own API.</p>
 *
 * <h2>Usage scenarios</h2>
 * <ul>
 *   <li><b>Convention (default case)</b> — field names match column names
 *       exactly and the PK is named {@code id}. The processor generates a
 *       {@code DefaultEntityMapper} inline; the developer writes nothing.</li>
 *   <li><b>Custom mapper</b> — names diverge or special handling is needed.
 *       The developer implements this interface and declares it via
 *       {@link io.simpleton.persistence.annotation.JdbcRepository#mapper()}.</li>
 * </ul>
 *
 * <h2>Three-method contract</h2>
 * <pre>{@code
 * public final class UserMapper implements EntityMapper<User> {
 *
 *     private static final List<ColumnDescriptor> COLUMNS = List.of(
 *         ColumnDescriptor.id("id",        "id"),
 *         ColumnDescriptor.of("firstName", "first_name"),
 *         ColumnDescriptor.of("lastName",  "last_name"),
 *         ColumnDescriptor.of("email",     "email")
 *     );
 *
 *     @Override
 *     public List<ColumnDescriptor> columns() { return COLUMNS; }
 *
 *     @Override
 *     public User toEntity(Map<String, Object> row) {
 *         User u = new User();
 *         u.setId(        (Long)   row.get("id"));
 *         u.setFirstName( (String) row.get("first_name"));
 *         u.setLastName(  (String) row.get("last_name"));
 *         u.setEmail(     (String) row.get("email"));
 *         return u;
 *     }
 *
 *     @Override
 *     public List<FieldValue> toFieldValues(User u) {
 *         // Return all non-generated columns with their values.
 *         // The caller filters on ColumnDescriptor flags as needed.
 *         return List.of(
 *             FieldValue.of(COLUMNS.get(0), u.getId()),
 *             FieldValue.of(COLUMNS.get(1), u.getFirstName()),
 *             FieldValue.of(COLUMNS.get(2), u.getLastName()),
 *             FieldValue.of(COLUMNS.get(3), u.getEmail())
 *         );
 *     }
 * }
 * }</pre>
 *
 * @param <T> the entity type being mapped
 */
public interface EntityMapper<T> {

    /**
     * Returns the ordered list of column descriptors for this entity.
     *
     * <p>The order is <em>stable and meaningful</em>: it governs the parameter
     * order in INSERT and UPDATE statements produced by the generated
     * implementation. The primary key must appear in the list (identified via
     * {@link ColumnDescriptor#id()}) and is handled specially by the back-end:</p>
     * <ul>
     *   <li>If {@link ColumnDescriptor#generated()} is {@code true}, it is
     *       excluded from INSERT and retrieved from the generated-keys result.</li>
     *   <li>It is always used in the {@code WHERE} clause of UPDATE, DELETE,
     *       and SELECT-by-id queries.</li>
     * </ul>
     *
     * @return immutable list of {@link ColumnDescriptor}, never {@code null} or empty
     */
    List<ColumnDescriptor> columns();

    /**
     * Builds an entity from a generic row produced by any query mechanism.
     *
     * <p>The {@code row} map contains one entry per column, keyed by the SQL
     * column name (as declared in {@link #columns()}). Values are whatever the
     * back-end fetched — typically the JDBC {@code rs.getObject()} return value
     * for a SQL back-end, or a document field value for a NoSQL back-end.</p>
     *
     * @param row column-name → value mapping for one result row; never {@code null}
     * @return populated entity, never {@code null}
     */
    T toEntity(Map<String, Object> row);

    /**
     * Extracts the field values to persist from an entity instance.
     *
     * <p>Returns <em>all</em> columns (including the PK) paired with their
     * current values. The back-end decides which entries to use based on the
     * {@link ColumnDescriptor} flags:</p>
     * <ul>
     *   <li>INSERT — skips entries where {@code column.generated() == true}.</li>
     *   <li>UPDATE SET — skips entries where {@code column.id() == true}.</li>
     *   <li>UPDATE WHERE / DELETE — uses only the entry where
     *       {@code column.id() == true}.</li>
     * </ul>
     *
     * <p>The list must respect the same order as {@link #columns()}.</p>
     *
     * @param entity the source entity; never {@code null}
     * @return list of {@link FieldValue} in {@link #columns()} order,
     *         never {@code null} or empty
     */
    List<FieldValue> toFieldValues(T entity);
}
