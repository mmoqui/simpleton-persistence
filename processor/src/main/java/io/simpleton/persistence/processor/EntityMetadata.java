package io.simpleton.persistence.processor;

import java.util.List;

/**
 * Internal data model extracted by the annotation processor for a single
 * {@code @JdbcRepository}-annotated interface.
 *
 * <p>Passed as-is to {@link io.simpleton.persistence.generator.RepositoryGenerator}.</p>
 */
public final class EntityMetadata {

  private final String entityFqn;          // e.g. com.example.User
  private final String entitySimpleName;   // e.g. User
  private final String idFqn;              // e.g. java.lang.Long
  private final String tableName;          // e.g. users
  private final List<FieldInfo> fields;    // private non-transient fields in declaration order
  private final FieldInfo idField;         // the "id" field
  private final String customMapperFqn;    // null when using the default convention

  public EntityMetadata(String entityFqn, String entitySimpleName,
                        String idFqn, String tableName,
                        List<FieldInfo> fields, FieldInfo idField,
                        String customMapperFqn) {
    this.entityFqn        = entityFqn;
    this.entitySimpleName = entitySimpleName;
    this.idFqn            = idFqn;
    this.tableName        = tableName;
    this.fields           = fields;
    this.idField          = idField;
    this.customMapperFqn  = customMapperFqn;
  }

  public String           getEntityFqn()        { return entityFqn; }
  public String           getEntitySimpleName() { return entitySimpleName; }
  public String           getIdFqn()            { return idFqn; }
  public String           getTableName()        { return tableName; }
  public List<FieldInfo>  getFields()           { return fields; }
  public FieldInfo        getIdField()          { return idField; }

  /** {@code null} when no custom mapper is declared. */
  public String           getCustomMapperFqn()  { return customMapperFqn; }

  public boolean          hasCustomMapper()      { return customMapperFqn != null; }

  // -------------------------------------------------------------------------

  /**
   * Describes a single {@code private} non-{@code transient} field of the entity.
   */
  public static final class FieldInfo {

    private final String  fieldName;  // e.g. firstName
    private final String  fieldFqn;  // e.g. java.lang.String
    private final boolean id;         // true when this is the "id" field

    public FieldInfo(String fieldName, String fieldFqn, boolean id) {
      this.fieldName = fieldName;
      this.fieldFqn  = fieldFqn;
      this.id        = id;
    }

    public String  getFieldName() { return fieldName; }
    public String  getFieldFqn()  { return fieldFqn; }
    public boolean isId()         { return id; }

    /**
     * Returns the capitalised field name for getter/setter synthesis.
     * Example: {@code "firstName"} → {@code "FirstName"}.
     */
    public String capitalized() {
      if (fieldName == null || fieldName.isEmpty()) return fieldName;
      return Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
    }
  }
}
