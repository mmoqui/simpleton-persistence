package io.github.jdbcprocessor.processor;

import com.google.auto.service.AutoService;
import io.github.jdbcprocessor.annotation.JdbcRepository;
import io.github.jdbcprocessor.annotation.Query;
import io.github.jdbcprocessor.annotation.Repository;
import io.github.jdbcprocessor.generator.RepositoryGenerator;
import io.github.jdbcprocessor.processor.EntityMetadata.FieldInfo;
import io.github.jdbcprocessor.processor.QueryMethodMetadata.ReturnKind;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Annotation processor that generates {@code XxxImpl} and {@code XxxFactory}
 * for every interface annotated with {@link JdbcRepository}.
 *
 * <h2>Processing flow</h2>
 * <ol>
 *   <li>Locate all {@code @JdbcRepository} interfaces in the current round.</li>
 *   <li>Validate that each one directly extends {@code Repository<T, ID>}.</li>
 *   <li>Extract the generic parameters T and ID.</li>
 *   <li>Collect the {@code private} non-{@code transient} fields of T.</li>
 *   <li>Resolve the custom mapper FQN (or {@code null} for convention).</li>
 *   <li>Collect all {@code @Query}-annotated methods: rewrite the SQL,
 *       validate parameter counts, determine the return kind.</li>
 *   <li>Build {@link EntityMetadata} + {@link QueryMethodMetadata} list and
 *       delegate generation to {@link RepositoryGenerator}.</li>
 * </ol>
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.jdbcprocessor.annotation.JdbcRepository")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class JdbcRepositoryProcessor extends AbstractProcessor {

    private final RepositoryGenerator generator = new RepositoryGenerator();

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(JdbcRepository.class)) {
            if (element.getKind() != ElementKind.INTERFACE) {
                error(element, "@JdbcRepository can only be applied to an interface.");
                continue;
            }
            try {
                processRepository((TypeElement) element);
            } catch (Exception e) {
                error(element, "Error processing @JdbcRepository: " + e.getMessage());
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------

    private void processRepository(TypeElement ifaceElement) throws IOException {
        JdbcRepository annotation = ifaceElement.getAnnotation(JdbcRepository.class);

        // 1. Resolve Repository<T, ID> generic parameters
        TypeMirror[] generics = resolveRepositoryGenerics(ifaceElement);
        if (generics == null) {
            error(ifaceElement, "The interface must directly extend Repository<T, ID>.");
            return;
        }
        TypeElement entityElement = (TypeElement) processingEnv.getTypeUtils().asElement(generics[0]);
        TypeElement idElement     = (TypeElement) processingEnv.getTypeUtils().asElement(generics[1]);
        if (entityElement == null || idElement == null) {
            error(ifaceElement, "Cannot resolve T or ID from Repository<T, ID>.");
            return;
        }

        // 2. Table name
        String tableName = annotation.table().isBlank()
                ? entityElement.getSimpleName().toString()
                : annotation.table();

        // 3. Collect entity fields
        List<FieldInfo> fields = new ArrayList<>();
        FieldInfo idField = null;

        for (Element enclosed : entityElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD) continue;
            VariableElement field = (VariableElement) enclosed;
            Set<Modifier> mods = field.getModifiers();
            if (mods.contains(Modifier.STATIC) || mods.contains(Modifier.TRANSIENT)) continue;
            if (!mods.contains(Modifier.PRIVATE)) continue;

            String fieldName = field.getSimpleName().toString();
            String fieldFqn  = processingEnv.getTypeUtils().erasure(field.asType()).toString();
            boolean isId     = fieldName.equals("id");

            FieldInfo fi = new FieldInfo(fieldName, fieldFqn, isId);
            fields.add(fi);
            if (isId) idField = fi;
        }

        if (idField == null) {
            error(ifaceElement,
                "Entity " + entityElement.getSimpleName() +
                " has no private field named 'id'. " +
                "By convention, the primary key must be named 'id'.");
            return;
        }

        // 4. Custom mapper FQN
        String customMapperFqn = resolveMapperFqn(ifaceElement);

        // 5. Collect @Query methods
        List<QueryMethodMetadata> queryMethods = collectQueryMethods(
                ifaceElement, entityElement, generics[0]);
        if (queryMethods == null) return; // validation error already reported

        // 6. Build metadata
        EntityMetadata meta = new EntityMetadata(
                entityElement.getQualifiedName().toString(),
                entityElement.getSimpleName().toString(),
                idElement.getQualifiedName().toString(),
                tableName, fields, idField, customMapperFqn);

        String ifaceFqn    = ifaceElement.getQualifiedName().toString();
        String packageName = processingEnv.getElementUtils()
                .getPackageOf(ifaceElement).getQualifiedName().toString();

        note(ifaceElement, "Generating " + packageName + "." + ifaceElement.getSimpleName() + "Impl");
        generator.generateImpl(ifaceFqn, packageName, meta, queryMethods)
                 .writeTo(processingEnv.getFiler());

        note(ifaceElement, "Generating " + packageName + "." + ifaceElement.getSimpleName() + "Factory");
        generator.generateFactory(ifaceFqn, packageName, meta)
                 .writeTo(processingEnv.getFiler());
    }

    // -------------------------------------------------------------------------
    // @Query method collection
    // -------------------------------------------------------------------------

    /**
     * Iterates over all methods declared directly in the interface (not inherited
     * from {@link Repository}) and processes those annotated with {@link Query}.
     *
     * @return the list of {@link QueryMethodMetadata}, or {@code null} if a
     *         validation error was reported (processing should abort)
     */
    private List<QueryMethodMetadata> collectQueryMethods(TypeElement ifaceElement,
                                                          TypeElement entityElement,
                                                          TypeMirror entityType) {
        List<QueryMethodMetadata> result = new ArrayList<>();
        String entityFqn = entityElement.getQualifiedName().toString();

        for (Element member : ifaceElement.getEnclosedElements()) {
            if (member.getKind() != ElementKind.METHOD) continue;
            ExecutableElement method = (ExecutableElement) member;

            Query queryAnnotation = method.getAnnotation(Query.class);
            if (queryAnnotation == null) continue;

            // Rewrite the SQL
            QueryRewriter.Result rewritten = QueryRewriter.rewrite(queryAnnotation.value());
            List<String> namedParams = rewritten.paramNames();

            // Collect method parameter names and types
            List<? extends VariableElement> methodParams = method.getParameters();

            // Validate: number of named params in query == number of method params
            if (namedParams.size() != methodParams.size()) {
                error(method,
                    "@Query on '" + method.getSimpleName() + "': " +
                    "query has " + namedParams.size() + " named parameter(s) but method has " +
                    methodParams.size() + " parameter(s). They must match.");
                return null;
            }

            List<String> paramNames = new ArrayList<>();
            List<String> paramTypes = new ArrayList<>();
            for (VariableElement p : methodParams) {
                paramNames.add(p.getSimpleName().toString());
                paramTypes.add(processingEnv.getTypeUtils().erasure(p.asType()).toString());
            }

            // Determine return kind
            ReturnKind returnKind = resolveReturnKind(method, entityType);
            if (returnKind == null) {
                error(method,
                    "@Query on '" + method.getSimpleName() + "': unsupported return type '" +
                    method.getReturnType() + "'. " +
                    "Supported: T, Optional<T>, List<T>, Set<T>.");
                return null;
            }

            result.add(new QueryMethodMetadata(
                    method.getSimpleName().toString(),
                    rewritten.sql(),
                    paramNames,
                    paramTypes,
                    returnKind,
                    entityFqn));
        }
        return result;
    }

    /**
     * Resolves the {@link ReturnKind} from the method's declared return type.
     * Returns {@code null} if the type is not one of the four supported forms.
     */
    private ReturnKind resolveReturnKind(ExecutableElement method, TypeMirror entityType) {
        TypeMirror returnType = method.getReturnType();

        // Erase generics for raw-type comparison
        TypeMirror erased = processingEnv.getTypeUtils().erasure(returnType);

        TypeElement listEl     = processingEnv.getElementUtils().getTypeElement("java.util.List");
        TypeElement setEl      = processingEnv.getElementUtils().getTypeElement("java.util.Set");
        TypeElement optionalEl = processingEnv.getElementUtils().getTypeElement("java.util.Optional");

        if (listEl != null && processingEnv.getTypeUtils()
                .isSameType(erased, processingEnv.getTypeUtils().erasure(listEl.asType()))) {
            return ReturnKind.LIST;
        }
        if (setEl != null && processingEnv.getTypeUtils()
                .isSameType(erased, processingEnv.getTypeUtils().erasure(setEl.asType()))) {
            return ReturnKind.SET;
        }
        if (optionalEl != null && processingEnv.getTypeUtils()
                .isSameType(erased, processingEnv.getTypeUtils().erasure(optionalEl.asType()))) {
            return ReturnKind.OPTIONAL;
        }
        // Check T directly: must be assignment-compatible with the entity type
        if (processingEnv.getTypeUtils()
                .isAssignable(processingEnv.getTypeUtils().erasure(entityType), erased)) {
            return ReturnKind.SINGLE;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TypeMirror[] resolveRepositoryGenerics(TypeElement ifaceElement) {
        TypeElement repositoryElement = processingEnv.getElementUtils()
                .getTypeElement(Repository.class.getCanonicalName());

        for (TypeMirror superIface : ifaceElement.getInterfaces()) {
            if (superIface.getKind() != TypeKind.DECLARED) continue;
            DeclaredType declared = (DeclaredType) superIface;
            if (declared.asElement().equals(repositoryElement)
                    && declared.getTypeArguments().size() == 2) {
                return new TypeMirror[]{
                        declared.getTypeArguments().get(0),
                        declared.getTypeArguments().get(1)
                };
            }
        }
        return null;
    }

    private String resolveMapperFqn(TypeElement ifaceElement) {
        for (AnnotationMirror am : ifaceElement.getAnnotationMirrors()) {
            if (!am.getAnnotationType().asElement().getSimpleName()
                    .contentEquals("JdbcRepository")) continue;
            for (var entry : am.getElementValues().entrySet()) {
                if (!entry.getKey().getSimpleName().contentEquals("mapper")) continue;
                TypeMirror mapperType = (TypeMirror) entry.getValue().getValue();
                String fqn = mapperType.toString();
                if (fqn.equals("java.lang.Void") || fqn.equals("void")) return null;
                return fqn;
            }
        }
        return null;
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private void note(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message, element);
    }
}
