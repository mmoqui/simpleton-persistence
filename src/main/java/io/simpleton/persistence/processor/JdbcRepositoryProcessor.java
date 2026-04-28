package io.simpleton.persistence.processor;

import io.simpleton.persistence.JdbcRepository;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

@SupportedAnnotationTypes("io.simpleton.persistence.JdbcRepository")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class JdbcRepositoryProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(JdbcRepository.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@JdbcRepository can only target interfaces.", element);
                continue;
            }

            try {
                generateRepositoryArtifacts((TypeElement) element);
            } catch (IOException exception) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "Code generation failed: " + exception.getMessage(), element);
            }
        }
        return true;
    }

    private void generateRepositoryArtifacts(TypeElement repositoryType) throws IOException {
        TypeMirror entityType = resolveEntityType(repositoryType);
        if (entityType == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Unable to resolve entity type. The interface must extend Repository<T>.", repositoryType);
            return;
        }

        String packageName = processingEnv.getElementUtils().getPackageOf(repositoryType).getQualifiedName().toString();
        String repositoryName = repositoryType.getSimpleName().toString();
        String entityTypeName = entityType.toString();
        String entitySimpleName = entityTypeName.substring(entityTypeName.lastIndexOf('.') + 1);
        String implementationName = repositoryName + "Impl";
        String factoryName = repositoryName + "Factory";

        JdbcRepository jdbcRepository = repositoryType.getAnnotation(JdbcRepository.class);
        String explicitTable = jdbcRepository.value() == null ? "" : jdbcRepository.value().trim();
        String tableName = explicitTable.isBlank() ? entitySimpleName : explicitTable;

        List<String> fields = extractEntityFields(entityType);
        if (fields.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Entity must declare at least one non-static field.", repositoryType);
            return;
        }
        if (!fields.contains("id")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Entity must define an 'id' field used as primary key.", repositoryType);
            return;
        }

        generateRepositoryImplementation(packageName, repositoryName, implementationName, entityTypeName, tableName, fields, repositoryType);
        generateRepositoryFactory(packageName, repositoryName, implementationName, factoryName, repositoryType);
    }

    private TypeMirror resolveEntityType(TypeElement repositoryType) {
        for (TypeMirror mirror : repositoryType.getInterfaces()) {
            if (!(mirror instanceof DeclaredType declaredType)) {
                continue;
            }
            if (declaredType.asElement() instanceof TypeElement typeElement
                    && typeElement.getQualifiedName().contentEquals("io.simpleton.persistence.Repository")
                    && !declaredType.getTypeArguments().isEmpty()) {
                return declaredType.getTypeArguments().getFirst();
            }
        }
        return null;
    }

    private List<String> extractEntityFields(TypeMirror entityType) {
        TypeElement typeElement = (TypeElement) processingEnv.getTypeUtils().asElement(entityType);
        List<String> fields = new ArrayList<>();
        for (var field : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            if (!field.getModifiers().contains(Modifier.STATIC)) {
                fields.add(field.getSimpleName().toString());
            }
        }
        return fields;
    }

    private void generateRepositoryImplementation(String packageName,
                                                  String repositoryName,
                                                  String implementationName,
                                                  String entityTypeName,
                                                  String tableName,
                                                  List<String> fields,
                                                  TypeElement repositoryType) throws IOException {
        String columns = String.join(", ", fields);
        String placeholders = repeat("?", fields.size(), ", ");
        String updateSet = fields.stream()
                .filter(field -> !"id".equals(field))
                .map(field -> field + " = ?")
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow(() -> new IllegalStateException("Entity must have at least one updatable field besides id."));

        String fieldHandleInitialization = buildFieldHandleInitialization(fields, entityTypeName);
        String saveParameters = buildSaveParameters(fields);
        String updateParameters = buildUpdateParameters(fields);
        String mapAssignments = buildMapAssignments(fields);

        String source = """
                package %s;

                import javax.sql.DataSource;
                import java.lang.invoke.MethodHandle;
                import java.lang.invoke.MethodHandles;
                import java.lang.reflect.Field;
                import java.sql.Connection;
                import java.sql.PreparedStatement;
                import java.sql.ResultSet;
                import java.sql.SQLException;
                import java.util.ArrayList;
                import java.util.HashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;

                public final class %s implements %s {
                    private static final String TABLE = "%s";
                    private static final String SQL_GET_BY_ID = "SELECT %s FROM %s WHERE id = ?";
                    private static final String SQL_FIND_ALL = "SELECT %s FROM %s";
                    private static final String SQL_INSERT = "INSERT INTO %s (%s) VALUES (%s)";
                    private static final String SQL_UPDATE = "UPDATE %s SET %s WHERE id = ?";
                    private static final String SQL_DELETE = "DELETE FROM %s WHERE id = ?";
                    private static final Map<String, MethodHandle> GETTERS = new HashMap<>();
                    private static final Map<String, MethodHandle> SETTERS = new HashMap<>();

                    static {
                        try {
                            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(%s.class, MethodHandles.lookup());
                %s        } catch (NoSuchFieldException | IllegalAccessException exception) {
                            throw new ExceptionInInitializerError(exception);
                        }
                    }

                    private final DataSource dataSource;

                    public %s(DataSource dataSource) {
                        this.dataSource = dataSource;
                    }

                    @Override
                    public Optional<%s> getById(Object id) {
                        try (Connection connection = dataSource.getConnection();
                             PreparedStatement statement = connection.prepareStatement(SQL_GET_BY_ID)) {
                            statement.setObject(1, id);
                            try (ResultSet resultSet = statement.executeQuery()) {
                                if (!resultSet.next()) {
                                    return Optional.empty();
                                }
                                return Optional.of(mapRow(resultSet));
                            }
                        } catch (SQLException exception) {
                            throw new IllegalStateException("SQL error while reading from table " + TABLE, exception);
                        }
                    }

                    @Override
                    public List<%s> findAll() {
                        try (Connection connection = dataSource.getConnection();
                             PreparedStatement statement = connection.prepareStatement(SQL_FIND_ALL);
                             ResultSet resultSet = statement.executeQuery()) {
                            List<%s> entities = new ArrayList<>();
                            while (resultSet.next()) {
                                entities.add(mapRow(resultSet));
                            }
                            return entities;
                        } catch (SQLException exception) {
                            throw new IllegalStateException("SQL error while reading all rows from table " + TABLE, exception);
                        }
                    }

                    @Override
                    public %s save(%s entity) {
                        try (Connection connection = dataSource.getConnection();
                             PreparedStatement statement = connection.prepareStatement(SQL_INSERT)) {
                %s            statement.executeUpdate();
                            return entity;
                        } catch (SQLException exception) {
                            throw new IllegalStateException("SQL error while inserting into table " + TABLE, exception);
                        }
                    }

                    @Override
                    public %s update(%s entity) {
                        try (Connection connection = dataSource.getConnection();
                             PreparedStatement statement = connection.prepareStatement(SQL_UPDATE)) {
                %s            statement.executeUpdate();
                            return entity;
                        } catch (SQLException exception) {
                            throw new IllegalStateException("SQL error while updating table " + TABLE, exception);
                        }
                    }

                    @Override
                    public boolean delete(Object id) {
                        try (Connection connection = dataSource.getConnection();
                             PreparedStatement statement = connection.prepareStatement(SQL_DELETE)) {
                            statement.setObject(1, id);
                            return statement.executeUpdate() > 0;
                        } catch (SQLException exception) {
                            throw new IllegalStateException("SQL error while deleting from table " + TABLE, exception);
                        }
                    }

                    private %s mapRow(ResultSet resultSet) {
                        try {
                            %s entity = new %s();
                %s            return entity;
                        } catch (ReflectiveOperationException | SQLException throwable) {
                            throw new IllegalStateException("Failed to map SQL row to entity.", throwable);
                        }
                    }

                    private Object read(%s entity, String field) {
                        try {
                            return GETTERS.get(field).invoke(entity);
                        } catch (Throwable throwable) {
                            throw new IllegalStateException("Unable to read field '" + field + "'.", throwable);
                        }
                    }

                    private void write(%s entity, String field, Object value) {
                        try {
                            SETTERS.get(field).invoke(entity, value);
                        } catch (Throwable throwable) {
                            throw new IllegalStateException("Unable to write field '" + field + "'.", throwable);
                        }
                    }
                }
                """.formatted(
                packageName,
                implementationName,
                repositoryName,
                tableName,
                columns,
                tableName,
                columns,
                tableName,
                tableName,
                columns,
                placeholders,
                tableName,
                updateSet,
                tableName,
                entityTypeName,
                fieldHandleInitialization,
                implementationName,
                entityTypeName,
                entityTypeName,
                entityTypeName,
                entityTypeName,
                entityTypeName,
                saveParameters,
                entityTypeName,
                entityTypeName,
                updateParameters,
                entityTypeName,
                entityTypeName,
                entityTypeName,
                mapAssignments,
                entityTypeName,
                entityTypeName
        );

        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + "." + implementationName, repositoryType);
        try (Writer writer = sourceFile.openWriter()) {
            writer.write(source);
        }
    }

    private void generateRepositoryFactory(String packageName,
                                           String repositoryName,
                                           String implementationName,
                                           String factoryName,
                                           TypeElement repositoryType) throws IOException {
        String source = """
                package %s;

                import io.simpleton.persistence.RepositoryFactory;
                import javax.sql.DataSource;

                public final class %s implements RepositoryFactory<%s> {

                    @Override
                    public %s create(DataSource dataSource) {
                        return new %s(dataSource);
                    }
                }
                """.formatted(packageName, factoryName, repositoryName, repositoryName, implementationName);

        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(packageName + "." + factoryName, repositoryType);
        try (Writer writer = sourceFile.openWriter()) {
            writer.write(source);
        }
    }

    private String buildFieldHandleInitialization(List<String> fields, String entityTypeName) {
        StringBuilder builder = new StringBuilder();
        for (String field : fields) {
            builder.append("            Field ").append(field).append("Field = ").append(entityTypeName)
                    .append(".class.getDeclaredField(\"").append(field).append("\");\n")
                    .append("            ").append(field).append("Field.setAccessible(true);\n")
                    .append("            GETTERS.put(\"").append(field).append("\", lookup.unreflectGetter(")
                    .append(field).append("Field));\n")
                    .append("            SETTERS.put(\"").append(field).append("\", lookup.unreflectSetter(")
                    .append(field).append("Field));\n");
        }
        return builder.toString();
    }

    private String buildSaveParameters(List<String> fields) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (String field : fields) {
            builder.append("            statement.setObject(").append(index++).append(", read(entity, \"")
                    .append(field).append("\"));\n");
        }
        return builder.toString();
    }

    private String buildUpdateParameters(List<String> fields) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (String field : fields) {
            if (!"id".equals(field)) {
                builder.append("            statement.setObject(").append(index++).append(", read(entity, \"")
                        .append(field).append("\"));\n");
            }
        }
        builder.append("            statement.setObject(").append(index).append(", read(entity, \"id\"));\n");
        return builder.toString();
    }

    private String buildMapAssignments(List<String> fields) {
        StringBuilder builder = new StringBuilder();
        for (String field : fields) {
            builder.append("            write(entity, \"").append(field).append("\", resultSet.getObject(\"")
                    .append(field).append("\"));\n");
        }
        return builder.toString();
    }

    private String repeat(String value, int count, String delimiter) {
        StringJoiner joiner = new StringJoiner(delimiter);
        for (int i = 0; i < count; i++) {
            joiner.add(value);
        }
        return joiner.toString();
    }
}
