package io.github.jdbcprocessor.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compile-time SQL rewriter for {@code @Query} annotations.
 *
 * <p>Performs two transformations on the raw query string:</p>
 * <ol>
 *   <li><b>Short-form SELECT expansion</b> — rewrites
 *       {@code SELECT alias FROM table alias} to
 *       {@code SELECT * FROM table alias}, preserving any trailing clauses.</li>
 *   <li><b>Named-parameter substitution</b> — replaces each {@code :name}
 *       token with a positional {@code ?}, recording the substitution order
 *       so the generated code can bind method arguments correctly.</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>
 * Input:  "SELECT u FROM users u WHERE u.username = :username AND u.age > :minAge"
 * Output sql:    "SELECT * FROM users u WHERE u.username = ? AND u.age > ?"
 * Output params: ["username", "minAge"]
 * </pre>
 */
public final class QueryRewriter {

    /**
     * Matches the short-form SELECT: {@code SELECT <alias> FROM <table> <alias>}
     * where alias and table are simple identifiers. The trailing content (WHERE,
     * ORDER BY, etc.) is captured in group 3.
     *
     * <p>Case-insensitive; allows optional whitespace.</p>
     */
    private static final Pattern SHORT_SELECT =
            Pattern.compile(
                "^\\s*SELECT\\s+(\\w+)\\s+FROM\\s+(\\S+)\\s+(\\w+)(.*?)$",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Matches a named parameter token such as {@code :username}. */
    private static final Pattern NAMED_PARAM = Pattern.compile(":([A-Za-z][A-Za-z0-9_]*)");

    private QueryRewriter() {}

    // -------------------------------------------------------------------------

    /**
     * Rewrites a raw {@code @Query} value into a positional-parameter SQL string
     * and returns the ordered list of substituted parameter names.
     *
     * @param rawQuery the annotation value as written by the developer
     * @return the rewrite result
     */
    public static Result rewrite(String rawQuery) {
        String expanded = expandShortSelect(rawQuery.trim());
        return substituteNamedParams(expanded);
    }

    // ── Short-form SELECT expansion ───────────────────────────────────────────

    /**
     * If the query starts with {@code SELECT alias FROM …}, replaces the alias
     * selector with {@code *}. Otherwise returns the query unchanged.
     *
     * <p>Examples:</p>
     * <pre>
     * "SELECT u FROM users u WHERE …"   → "SELECT * FROM users u WHERE …"
     * "SELECT * FROM users u WHERE …"   → unchanged
     * "SELECT id, name FROM users …"    → unchanged (not a single-token selector)
     * </pre>
     */
    static String expandShortSelect(String sql) {
        Matcher m = SHORT_SELECT.matcher(sql);
        if (!m.matches()) return sql;

        String alias  = m.group(1);  // e.g. "u"
        String table  = m.group(2);  // e.g. "users"
        String alias2 = m.group(3);  // e.g. "u"  (repeated after FROM)
        String rest   = m.group(4);  // e.g. " WHERE u.age > :minAge"

        // Only expand when the two tokens are the same identifier
        // (genuine alias, not SELECT col FROM table alias)
        if (!alias.equalsIgnoreCase(alias2)) return sql;

        return "SELECT * FROM " + table + " " + alias2 + rest;
    }

    // ── Named-parameter substitution ──────────────────────────────────────────

    /**
     * Replaces every {@code :name} token with {@code ?} and records the names
     * in substitution order.
     */
    static Result substituteNamedParams(String sql) {
        List<String> paramNames = new ArrayList<>();
        Matcher m = NAMED_PARAM.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            paramNames.add(m.group(1));
            m.appendReplacement(sb, "?");
        }
        m.appendTail(sb);
        return new Result(sb.toString(), paramNames);
    }

    // -------------------------------------------------------------------------

    /**
     * Holds the output of a rewrite operation.
     *
     * @param sql        the rewritten SQL with positional {@code ?} placeholders
     * @param paramNames ordered list of original parameter names, one per {@code ?}
     */
    public record Result(String sql, List<String> paramNames) {}
}
