package io.simpleton.persistence.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QueryRewriter} — the compile-time SQL transformer.
 *
 * <p>These tests run without any JDBC or annotation-processing infrastructure;
 * {@code QueryRewriter} is a pure function on strings.</p>
 */
class QueryRewriterTest {

    // ── Short-form SELECT expansion ───────────────────────────────────────────

    @Test
    @DisplayName("Short-form SELECT is expanded to SELECT *")
    void shortFormSelectExpanded() {
        QueryRewriter.Result r = QueryRewriter.rewrite(
                "SELECT u FROM users u WHERE u.age > :minAge");
        assertTrue(r.sql().startsWith("SELECT * FROM users u WHERE"),
                "Expected 'SELECT * FROM …', got: " + r.sql());
    }

    @Test
    @DisplayName("SELECT * is left unchanged")
    void fullSelectUnchanged() {
        String sql = "SELECT * FROM users u WHERE u.id = :id";
        QueryRewriter.Result r = QueryRewriter.rewrite(sql);
        assertTrue(r.sql().startsWith("SELECT * FROM users u WHERE"),
                "Expected unchanged SELECT *, got: " + r.sql());
    }

    @Test
    @DisplayName("Multi-column SELECT is left unchanged")
    void multiColumnSelectUnchanged() {
        String sql = "SELECT id, username FROM users WHERE id = :id";
        QueryRewriter.Result r = QueryRewriter.rewrite(sql);
        assertTrue(r.sql().contains("SELECT id, username"),
                "Multi-column SELECT must not be rewritten");
    }

    // ── Named-parameter substitution ──────────────────────────────────────────

    @Test
    @DisplayName("Single named parameter replaced by positional ?")
    void singleNamedParam() {
        QueryRewriter.Result r = QueryRewriter.rewrite(
                "SELECT * FROM users WHERE username = :username");
        assertEquals("SELECT * FROM users WHERE username = ?", r.sql());
        assertEquals(List.of("username"), r.paramNames());
    }

    @Test
    @DisplayName("Multiple named parameters replaced in order")
    void multipleNamedParams() {
        QueryRewriter.Result r = QueryRewriter.rewrite(
                "SELECT u FROM users u WHERE u.username = :username AND u.age > :minAge");
        assertEquals("SELECT * FROM users u WHERE u.username = ? AND u.age > ?", r.sql());
        assertEquals(List.of("username", "minAge"), r.paramNames());
    }

    @Test
    @DisplayName("Same parameter name appearing twice substituted twice")
    void duplicateNamedParam() {
        QueryRewriter.Result r = QueryRewriter.rewrite(
                "SELECT * FROM users WHERE age > :age AND age < :age");
        assertEquals("SELECT * FROM users WHERE age > ? AND age < ?", r.sql());
        assertEquals(List.of("age", "age"), r.paramNames());
    }

    @Test
    @DisplayName("Query with no named parameters returns empty param list")
    void noNamedParams() {
        QueryRewriter.Result r = QueryRewriter.rewrite("SELECT * FROM users");
        assertEquals("SELECT * FROM users", r.sql());
        assertTrue(r.paramNames().isEmpty());
    }

    // ── Combined transformations ───────────────────────────────────────────────

    @Test
    @DisplayName("Short-form SELECT and named parameters both transformed")
    void shortFormAndNamedParams() {
        QueryRewriter.Result r = QueryRewriter.rewrite(
                "SELECT u FROM users u WHERE u.username = :username AND u.age >= :minAge");
        assertEquals("SELECT * FROM users u WHERE u.username = ? AND u.age >= ?", r.sql());
        assertEquals(List.of("username", "minAge"), r.paramNames());
    }

    @Test
    @DisplayName("Leading/trailing whitespace is trimmed before processing")
    void whitespaceIsTrimmed() {
        QueryRewriter.Result r = QueryRewriter.rewrite(
                "  SELECT u FROM users u WHERE u.id = :id  ");
        assertFalse(r.sql().isBlank());
        assertEquals(List.of("id"), r.paramNames());
    }
}
