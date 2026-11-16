package io.simpleton.persistence.test;

import io.simpleton.persistence.test.entity.Product;
import io.simpleton.persistence.test.repository.ProductRepository;
import io.simpleton.persistence.test.repository.ProductRepositoryFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ProductRepository} — <b>custom mapper case</b>.
 *
 * <h2>What is validated beyond basic CRUD</h2>
 * <ol>
 *   <li><b>Custom mapper injection</b> — {@code ProductRepositoryFactory} injects
 *       {@code ProductMapper} (SQL column names differ from Java field names);
 *       the mappings {@code label ↔ product_label}, {@code unitPrice ↔ unit_price},
 *       and {@code stockQty ↔ stock_qty} must be transparent to the caller.</li>
 *   <li><b>Correct SQL column names</b> — a direct JDBC query confirms that data
 *       is stored under the SQL column names, not the Java field names.</li>
 *   <li><b>Entity independence</b> — {@link Product} imports no type from the
 *       persistence framework.</li>
 * </ol>
 *
 * <p>All tests run against an in-memory H2 database created before the test
 * class and dropped afterwards.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductRepositoryIntegrationTest {

    private static DataSource        dataSource;
    private static ProductRepository repository;
    private static Long              savedId;

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS products (
                id            BIGINT AUTO_INCREMENT PRIMARY KEY,
                product_label VARCHAR(200)     NOT NULL,
                unit_price    DOUBLE PRECISION NOT NULL,
                stock_qty     INT              NOT NULL DEFAULT 0
            )
            """;

    @BeforeAll
    static void setUp() throws SQLException {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:productdb;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(DDL);
        }
        repository = new ProductRepositoryFactory(dataSource).create();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS products");
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("Factory with custom mapper returns a ProductRepository")
    void factoryProvidesRepository() {
        assertNotNull(repository);
        assertInstanceOf(ProductRepository.class, repository);
    }

    @Test @Order(2)
    @DisplayName("save() writes rows under the SQL column names (product_label, unit_price, …)")
    void saveUsesCorrectColumnNames() throws SQLException {
        Product p = new Product("Widget Pro", 29.99, 100);
        Product saved = repository.save(p);

        assertNotNull(saved.getId());
        savedId = saved.getId();

        // Direct JDBC check: data must be stored under the SQL column names,
        // not the Java field names (label, unitPrice, stockQty).
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT product_label, unit_price, stock_qty FROM products WHERE id = ?")) {
            ps.setLong(1, savedId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Row must exist");
                assertEquals("Widget Pro", rs.getString("product_label"));
                assertEquals(29.99,        rs.getDouble("unit_price"), 0.001);
                assertEquals(100,          rs.getInt("stock_qty"));
            }
        }
    }

    @Test @Order(3)
    @DisplayName("findById() maps product_label→label, unit_price→unitPrice, stock_qty→stockQty")
    void findByIdMapsColumnsToJavaFields() {
        assertNotNull(savedId);
        Optional<Product> result = repository.findById(savedId);

        assertTrue(result.isPresent());
        Product p = result.get();
        assertEquals(savedId,      p.getId());
        assertEquals("Widget Pro", p.getLabel());
        assertEquals(29.99,        p.getUnitPrice(), 0.001);
        assertEquals(100,          p.getStockQty());
    }

    @Test @Order(4)
    @DisplayName("findById() returns Optional.empty() for an unknown id")
    void findByIdUnknown() {
        assertFalse(repository.findById(Long.MAX_VALUE).isPresent());
    }

    @Test @Order(5)
    @DisplayName("findAll() returns all products with correct mapping")
    void findAllReturnsAllMapped() {
        repository.save(new Product("Gadget Basic", 9.99,  50));
        repository.save(new Product("Super Tool",   49.99, 10));

        List<Product> all = repository.findAll();
        assertTrue(all.size() >= 3);

        // Verify mapping is correct for every returned product
        all.forEach(p -> {
            assertNotNull(p.getId(),        "id must not be null");
            assertNotNull(p.getLabel(),     "label (← product_label) must not be null");
            assertNotNull(p.getUnitPrice(), "unitPrice (← unit_price) must not be null");
            assertNotNull(p.getStockQty(),  "stockQty (← stock_qty) must not be null");
        });
    }

    @Test @Order(6)
    @DisplayName("update() modifies all non-PK columns via the custom mapper")
    void updateModifiesAllNonPkColumns() {
        assertNotNull(savedId);
        Product p = repository.findById(savedId).orElseThrow();
        p.setLabel("Widget Pro V2");
        p.setUnitPrice(34.99);
        p.setStockQty(80);
        repository.update(p);

        Product reloaded = repository.findById(savedId).orElseThrow();
        assertEquals("Widget Pro V2", reloaded.getLabel());
        assertEquals(34.99,           reloaded.getUnitPrice(), 0.001);
        assertEquals(80,              reloaded.getStockQty());
        assertEquals(savedId,         reloaded.getId()); // PK must not change
    }

    @Test @Order(7)
    @DisplayName("count() is consistent with the actual number of rows")
    void countIsConsistent() {
        assertEquals(repository.findAll().size(), repository.count());
    }

    @Test @Order(8)
    @DisplayName("deleteById() removes the row; confirmed by a direct SQL query")
    void deleteByIdRemovesRow() throws SQLException {
        assertNotNull(savedId);
        long before = repository.count();
        repository.deleteById(savedId);
        assertEquals(before - 1, repository.count());
        assertFalse(repository.findById(savedId).isPresent());

        // Confirm via direct SQL that the row is gone
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM products WHERE id = ?")) {
            ps.setLong(1, savedId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }

    @Test @Order(9)
    @DisplayName("Product entity carries no annotation from the persistence framework")
    void productEntityHasNoPersistenceAnnotations() throws ClassNotFoundException {
        Class<?> productClass = Class.forName(
                "io.simpleton.persistence.test.entity.Product");

        assertEquals(0, productClass.getAnnotations().length,
                "Product must carry no class-level annotation");

        java.util.Arrays.stream(productClass.getDeclaredFields()).forEach(f ->
            assertEquals(0, f.getAnnotations().length,
                    "Field '" + f.getName() + "' must carry no annotation"));
    }

    @Test @Order(10)
    @DisplayName("Bulk insert: every product retrievable with correct mapping")
    void bulkInsertRoundTrip() {
        long before = repository.count();
        for (int i = 1; i <= 3; i++) {
            repository.save(new Product("Bulk " + i, i * 10.0, i * 5));
        }
        List<Product> all = repository.findAll();
        assertEquals(before + 3, all.size());

        all.stream()
           .filter(p -> p.getLabel() != null && p.getLabel().startsWith("Bulk"))
           .forEach(p -> {
               assertNotNull(p.getUnitPrice(), "unitPrice must not be null");
               assertNotNull(p.getStockQty(),  "stockQty must not be null");
           });
    }

    @Test @Order(11)
    @DisplayName("ProductMapper has no dependency on JDBC types")
    void customMapperIsJdbcFree() {
        Class<?> mapperClass = io.simpleton.persistence.test.mapper.ProductMapper.class;

        for (java.lang.reflect.Method m : mapperClass.getDeclaredMethods()) {
            for (Class<?> param : m.getParameterTypes()) {
                assertFalse(
                    param.getName().startsWith("java.sql."),
                    "Method '" + m.getName() + "' must not have a JDBC parameter type, found: " + param.getName());
            }
            assertFalse(
                m.getReturnType().getName().startsWith("java.sql."),
                "Method '" + m.getName() + "' must not return a JDBC type");
        }
    }
}
