package io.simpleton.persistence.test;

import io.simpleton.persistence.test.entity.User;
import io.simpleton.persistence.test.repository.UserRepository;
import io.simpleton.persistence.test.repository.UserRepositoryFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link UserRepository} — <b>convention case</b>.
 *
 * <h2>What is validated</h2>
 * <ol>
 *   <li><b>Factory and DefaultEntityMapper</b> — {@link UserRepositoryFactory}
 *       provides a working {@link UserRepository} without the test ever
 *       referencing {@code UserRepositoryImpl} or any mapper class.</li>
 *   <li><b>VarHandle field access</b> — {@code toEntity} populates the entity
 *       without setters; {@code bind} reads field values without getters.</li>
 *   <li><b>save</b> — INSERT and retrieval of the auto-generated key.</li>
 *   <li><b>findById</b> — SELECT by PK with correct field mapping.</li>
 *   <li><b>findAll</b> — returns all rows.</li>
 *   <li><b>update</b> — UPDATE; new values are readable afterwards.</li>
 *   <li><b>count</b> — reflects the number of rows.</li>
 *   <li><b>deleteById</b> — removes the row and makes it invisible.</li>
 *   <li><b>Nullable field round-trip</b> — a {@code null} value survives
 *       a save/findById cycle.</li>
 * </ol>
 *
 * <p>All tests run against an in-memory H2 database created before the test
 * class and dropped afterwards.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserRepositoryIntegrationTest {

  private static DataSource     dataSource;
  private static UserRepository repository;
  private static Long           savedId;

  private static final String DDL = """
          CREATE TABLE IF NOT EXISTS users (
        id       BIGINT AUTO_INCREMENT PRIMARY KEY,
        username VARCHAR(100) NOT NULL,
        email    VARCHAR(200) NOT NULL,
        age      INT
          )
          """;

  @BeforeAll
  static void setUp() throws SQLException {
    JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:userdb;DB_CLOSE_DELAY=-1");
    ds.setUser("sa");
    ds.setPassword("");
    dataSource = ds;
    try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
      s.execute(DDL);
    }
    // Only entry point: the generated factory
    repository = new UserRepositoryFactory(dataSource).create();
  }

  @AfterAll
  static void tearDown() throws SQLException {
    try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
      s.execute("DROP TABLE IF EXISTS users");
    }
  }

  // ── Tests ─────────────────────────────────────────────────────────────────

  @Test @Order(1)
  @DisplayName("Factory returns a UserRepository (without referencing the Impl)")
  void factoryProvidesRepository() {
    assertNotNull(repository);
    assertInstanceOf(UserRepository.class, repository);
    assertFalse(repository.getClass().getSimpleName().contains("Factory"),
        "create() must not return the factory itself");
  }

  @Test @Order(2)
  @DisplayName("save() inserts a row and populates the auto-generated id")
  void savePopulatesGeneratedId() {
    User u = new User("alice", "alice@example.com", 30);
    User saved = repository.save(u);

    assertNotNull(saved.getId(), "id must not be null after save()");
    assertTrue(saved.getId() > 0, "id must be positive");
    savedId = saved.getId();
  }

  @Test @Order(3)
  @DisplayName("findById() returns the entity with all fields correctly mapped")
  void findByIdMapsAllFields() {
    assertNotNull(savedId);
    Optional<User> result = repository.findById(savedId);

    assertTrue(result.isPresent());
    User u = result.get();
    assertEquals(savedId,             u.getId());
    assertEquals("alice",             u.getUsername());
    assertEquals("alice@example.com", u.getEmail());
    assertEquals(30,                  u.getAge());
  }

  @Test @Order(4)
  @DisplayName("findById() returns Optional.empty() for an unknown id")
  void findByIdUnknown() {
    assertFalse(repository.findById(Long.MAX_VALUE).isPresent());
  }

  @Test @Order(5)
  @DisplayName("findAll() returns all rows after multiple inserts")
  void findAllReturnsAllRows() {
    repository.save(new User("bob",     "bob@example.com",     25));
    repository.save(new User("charlie", "charlie@example.com", 35));

    List<User> all = repository.findAll();
    assertTrue(all.size() >= 3);
    assertTrue(all.stream().anyMatch(u -> "alice".equals(u.getUsername())));
    assertTrue(all.stream().anyMatch(u -> "bob".equals(u.getUsername())));
    assertTrue(all.stream().anyMatch(u -> "charlie".equals(u.getUsername())));
  }

  @Test @Order(6)
  @DisplayName("update() persists changed values")
  void updatePersistsChanges() {
    assertNotNull(savedId);
    User u = repository.findById(savedId).orElseThrow();
    u.setEmail("alice-new@example.com");
    u.setAge(31);
    repository.update(u);

    User reloaded = repository.findById(savedId).orElseThrow();
    assertEquals("alice-new@example.com", reloaded.getEmail());
    assertEquals(31,                       reloaded.getAge());
    assertEquals("alice",                  reloaded.getUsername()); // unchanged
  }

  @Test @Order(7)
  @DisplayName("count() matches findAll().size()")
  void countMatchesFindAllSize() {
    assertEquals(repository.findAll().size(), repository.count());
    assertTrue(repository.count() >= 3);
  }

  @Test @Order(8)
  @DisplayName("deleteById() removes the row; findById() returns empty afterwards")
  void deleteByIdRemovesRow() {
    assertNotNull(savedId);
    long before = repository.count();
    repository.deleteById(savedId);
    assertEquals(before - 1, repository.count());
    assertFalse(repository.findById(savedId).isPresent());
  }

  @Test @Order(9)
  @DisplayName("Bulk insert: 5 entities all retrievable")
  void bulkInsertAndRetrieve() {
    long before = repository.count();
    for (int i = 0; i < 5; i++) {
      repository.save(new User("bulk" + i, "bulk" + i + "@test.com", 20 + i));
    }
    List<User> all = repository.findAll();
    assertEquals(before + 5, all.size());
    assertTrue(all.stream().anyMatch(u -> "bulk0".equals(u.getUsername())));
    assertTrue(all.stream().anyMatch(u -> "bulk4".equals(u.getUsername())));
  }

  @Test @Order(10)
  @DisplayName("Nullable field survives a save/findById round-trip")
  void nullableFieldRoundTrip() {
    User u = new User("noage", "noage@test.com", null);
    User saved = repository.save(u);
    assertNotNull(saved.getId());

    User reloaded = repository.findById(saved.getId()).orElseThrow();
    assertNull(reloaded.getAge(), "null field must remain null after round-trip");
  }

  @Test @Order(11)
  @DisplayName("DefaultEntityMapper has no dependency on JDBC types")
  void defaultMapperIsJdbcFree() throws Exception {
    // Retrieve the DefaultEntityMapper class from inside UserRepositoryFactory
    Class<?> factoryClass = UserRepositoryFactory.class;
    Class<?> mapperClass = java.util.Arrays.stream(factoryClass.getDeclaredClasses())
        .filter(c -> c.getSimpleName().equals("DefaultEntityMapper"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("DefaultEntityMapper inner class not found"));

    // Verify that no method parameter or return type references a JDBC type
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

  // ── @Query method tests ───────────────────────────────────────────────────

  @Test @Order(12)
  @DisplayName("@Query List<T>: findByAgeGreaterThan returns matching users")
  void queryListReturnType() {
    repository.save(new User("young",  "young@test.com",  15));
    repository.save(new User("adult",  "adult@test.com",  25));
    repository.save(new User("senior", "senior@test.com", 65));

    List<User> over20 = repository.findByAgeGreaterThan(20);
    assertTrue(over20.stream().anyMatch(u -> "adult".equals(u.getUsername())));
    assertTrue(over20.stream().anyMatch(u -> "senior".equals(u.getUsername())));
    over20.forEach(u -> assertTrue(u.getAge() > 20,
        "All returned users must have age > 20, found: " + u.getAge()));
  }

  @Test @Order(13)
  @DisplayName("@Query Optional<T>: findByUsername returns present when found")
  void queryOptionalFound() {
    repository.save(new User("uniqueuser", "unique@test.com", 30));
    Optional<User> result = repository.findByUsername("uniqueuser");
    assertTrue(result.isPresent());
    assertEquals("uniqueuser", result.get().getUsername());
  }

  @Test @Order(14)
  @DisplayName("@Query Optional<T>: findByUsername returns empty when not found")
  void queryOptionalEmpty() {
    assertFalse(repository.findByUsername("nobody_xyz").isPresent());
  }

  @Test @Order(15)
  @DisplayName("@Query T: getByUsername returns the entity when found")
  void querySingleFound() {
    repository.save(new User("directuser", "direct@test.com", 40));
    User user = repository.getByUsername("directuser");
    assertNotNull(user);
    assertEquals("directuser", user.getUsername());
  }

  @Test @Order(16)
  @DisplayName("@Query T: getByUsername throws NotFoundException when absent")
  void querySingleThrowsNotFoundException() {
    assertThrows(
      io.simpleton.persistence.annotation.NotFoundException.class,
      () -> repository.getByUsername("ghost_user_xyz"),
      "Expected NotFoundException for a missing entity");
  }

  @Test @Order(17)
  @DisplayName("@Query multi-param: findByUsernamePatternAndMinAge filters correctly")
  void queryMultiParam() {
    repository.save(new User("alice30", "a30@test.com", 30));
    repository.save(new User("alice20", "a20@test.com", 20));
    repository.save(new User("bob30",   "b30@test.com", 30));

    List<User> result = repository.findByUsernamePatternAndMinAge("alice%", 25);
    assertTrue(result.stream().anyMatch(u -> "alice30".equals(u.getUsername())));
    assertTrue(result.stream().noneMatch(u -> "alice20".equals(u.getUsername())),
        "alice20 (age=20) must be filtered out by minAge=25");
    assertTrue(result.stream().noneMatch(u -> "bob30".equals(u.getUsername())),
        "bob30 must be filtered out by the alice% pattern");
  }
}
