package io.simpleton.persistence.annotation;

/**
 * Associates a {@link ColumnDescriptor} with the concrete value extracted
 * from an entity instance.
 *
 * <p>Returned by {@link io.simpleton.persistence.mapper.EntityMapper#toFieldValues(Object)}
 * so that any persistence back-end (SQL, MongoDB, …) can bind the values
 * to its own query mechanism without the mapper knowing about it.</p>
 *
 * <p>Example — SQL back-end usage inside a generated {@code XxxImpl}:</p>
 * <pre>{@code
 * List<FieldValue> fields = mapper.toFieldValues(entity);
 * for (int i = 0; i < fields.size(); i++) {
 *     ps.setObject(i + 1, fields.get(i).value());
 * }
 * }</pre>
 *
 * @param column the column descriptor (name, id flag, generated flag)
 * @param value  the runtime value for that column; may be {@code null}
 */
  public record FieldValue(ColumnDescriptor column, Object value) {

    /** Convenience factory. */
    public static FieldValue of(ColumnDescriptor column, Object value) {
      return new FieldValue(column, value);
    }
  }
