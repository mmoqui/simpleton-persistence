package io.github.jdbcprocessor.processor;

import java.util.List;

/**
 * Internal model of a single {@code @Query}-annotated method collected by
 * {@link JdbcRepositoryProcessor} and consumed by
 * {@link io.github.jdbcprocessor.generator.RepositoryGenerator}.
 */
public final class QueryMethodMetadata {

    /** The method name as declared in the interface (e.g. {@code findByEmail}). */
    private final String methodName;

    /**
     * The SQL string after compile-time rewriting:
     * <ul>
     *   <li>Short-form SELECT expanded to {@code SELECT * FROM …}</li>
     *   <li>Named parameters ({@code :name}) replaced by positional {@code ?}</li>
     * </ul>
     */
    private final String rewrittenSql;

    /**
     * Ordered list of method parameter names that correspond to each {@code ?}
     * in {@link #rewrittenSql}, in substitution order.
     */
    private final List<String> paramNames;

    /**
     * Ordered list of fully-qualified types of the method parameters,
     * parallel to {@link #paramNames}.
     */
    private final List<String> paramTypes;

    /** The return kind, determining how the ResultSet is consumed. */
    private final ReturnKind returnKind;

    /**
     * Fully-qualified type of the entity produced by the query
     * (same as the entity type managed by the repository).
     */
    private final String entityFqn;

    public QueryMethodMetadata(String methodName,
                               String rewrittenSql,
                               List<String> paramNames,
                               List<String> paramTypes,
                               ReturnKind returnKind,
                               String entityFqn) {
        this.methodName   = methodName;
        this.rewrittenSql = rewrittenSql;
        this.paramNames   = paramNames;
        this.paramTypes   = paramTypes;
        this.returnKind   = returnKind;
        this.entityFqn    = entityFqn;
    }

    public String       getMethodName()   { return methodName; }
    public String       getRewrittenSql() { return rewrittenSql; }
    public List<String> getParamNames()   { return paramNames; }
    public List<String> getParamTypes()   { return paramTypes; }
    public ReturnKind   getReturnKind()   { return returnKind; }
    public String       getEntityFqn()    { return entityFqn; }

    // -------------------------------------------------------------------------

    /**
     * Classifies the return type of a {@code @Query} method.
     */
    public enum ReturnKind {
        /** {@code List<T>} — all matching rows. */
        LIST,
        /** {@code Set<T>} — all matching rows, deduplicated. */
        SET,
        /** {@code Optional<T>} — zero or one row. */
        OPTIONAL,
        /** {@code T} directly — exactly one row; throws {@link
         *  io.github.jdbcprocessor.annotation.NotFoundException} if absent. */
        SINGLE
    }
}
