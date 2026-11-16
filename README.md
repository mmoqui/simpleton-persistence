# simpleton-persistence

A simple JDBC persistence framework built upon annotations.

`simpleton-persistence` generates JDBC repositories **at compile time** from plain Java
interfaces. You declare a repository interface, annotate it with `@JdbcRepository`, and an
annotation processor writes the JDBC implementation and its factory for you. You don't need
a runtime container, proxies, reflection-based ORM magic, or persistence annotations on your
entities.

## Features

- **Compile-time code generation**: the annotation processor (built with
  [JavaPoet](https://github.com/square/javapoet)) generates an `XxxImpl` class with pre-built
  SQL statements and an `XxxFactory` class to instantiate it.
- **Out-of-the-box CRUD**: `findById`, `findAll`, `save`, `update`, `deleteById` and `count`
  come with the `Repository<T, ID>` contract.
- **Custom queries with `@Query`**: plain SQL with named parameters (`:name`) and a short-form
  `SELECT alias FROM table alias` syntax.
- **Persistence-agnostic entities**: entities are POJOs with no annotations. Mapping is either
  inferred by convention or provided by a custom `EntityMapper`.
- **No runtime reflection on the hot path**: the default mapper accesses private fields through
  `VarHandle`s that are resolved once.
- **JPMS friendly**: the framework is a named Java module, and the processor is discovered
  automatically through `provides javax.annotation.processing.Processor`.

## Requirements

- Java 21+
- Maven 3.x

## Project layout

| Module      | Artifact                          | Description                                                       |
|-------------|-----------------------------------|-------------------------------------------------------------------|
| `processor` | `jdbc-annotation-processor`       | Public API (annotations, contracts) and the annotation processor. |
| `test-app`  | `jdbc-annotation-processor-test`  | Example application and integration tests on an in-memory H2 database. |

## Getting started

### 1. Add the dependency and register the processor

```xml
<dependency>
    <groupId>io.simpleton.persistence</groupId>
    <artifactId>jdbc-annotation-processor</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.simpleton.persistence</groupId>
                <artifactId>jdbc-annotation-processor</artifactId>
                <version>1.0.0-SNAPSHOT</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

### 2. Write an entity

The entity is a plain class. By convention, its `private`, non-`static`, non-`transient`
fields are mapped to columns with the same name, and the primary key field must be named `id`.

```java
public class User {
    private Long    id;
    private String  username;
    private String  email;
    private Integer age;

    public User() {}
    // getters / setters ...
}
```

```sql
CREATE TABLE users (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email    VARCHAR(200) NOT NULL,
    age      INT
);
```

### 3. Declare the repository

```java
@JdbcRepository(table = "users")
public interface UserRepository extends Repository<User, Long> {

    @Query("SELECT u FROM users u WHERE u.age > :minAge")
    List<User> findByAgeGreaterThan(int minAge);

    @Query("SELECT u FROM users u WHERE u.username = :username")
    Optional<User> findByUsername(String username);

    @Query("SELECT u FROM users u WHERE u.username = :username")
    User getByUsername(String username); // throws NotFoundException if absent
}
```

If `table` is omitted, the table name defaults to the entity's simple name.

### 4. Use it

At compile time, the processor generates `UserRepositoryImpl` and `UserRepositoryFactory` in
the same package as the interface:

```java
DataSource dataSource = ...;
UserRepository users = new UserRepositoryFactory(dataSource).create();

User alice = users.save(new User("alice", "alice@example.com", 30)); // id is set from the generated key
Optional<User> found = users.findByUsername("alice");
List<User> adults = users.findByAgeGreaterThan(17);
long total = users.count();
```

## Custom queries

`@Query` takes standard SQL with two conveniences:

- **Short-form SELECT**: `SELECT u FROM users u ...` is rewritten to `SELECT * FROM users u ...`
  at compile time.
- **Named parameters**: `:name` placeholders are bound to the method parameters in the order
  they appear in the query.

Supported return types:

| Return type   | Behaviour                                                     |
|---------------|---------------------------------------------------------------|
| `List<T>`     | All matching rows.                                            |
| `Set<T>`      | All matching rows, deduplicated.                              |
| `Optional<T>` | At most one row; empty if none.                               |
| `T`           | Exactly one row; throws `NotFoundException` if none matches.  |

An unsupported return type is reported as a compilation error.

## Custom mapping

When the field names don't match the column names, write an `EntityMapper<T>` and reference it
from the annotation:

```java
@JdbcRepository(table = "products", mapper = ProductMapper.class)
public interface ProductRepository extends Repository<Product, Long> {}
```

```java
public final class ProductMapper implements EntityMapper<Product> {

    private static final List<ColumnDescriptor> COLUMNS = List.of(
            ColumnDescriptor.id("id",        "id"),            // auto-generated PK
            ColumnDescriptor.of("label",     "product_label"),
            ColumnDescriptor.of("unitPrice", "unit_price"),
            ColumnDescriptor.of("stockQty",  "stock_qty"));

    @Override
    public List<ColumnDescriptor> columns() { return COLUMNS; }

    @Override
    public Product toEntity(Map<String, Object> row) {
        Product p = new Product();
        p.setId((Long) row.get("id"));
        p.setLabel((String) row.get("product_label"));
        p.setUnitPrice((Double) row.get("unit_price"));
        p.setStockQty((Integer) row.get("stock_qty"));
        return p;
    }

    @Override
    public List<FieldValue> toFieldValues(Product p) {
        return List.of(
                FieldValue.of(COLUMNS.get(0), p.getId()),
                FieldValue.of(COLUMNS.get(1), p.getLabel()),
                FieldValue.of(COLUMNS.get(2), p.getUnitPrice()),
                FieldValue.of(COLUMNS.get(3), p.getStockQty()));
    }
}
```

The mapper knows nothing about JDBC: it works with a `Map<String, Object>` row and a list of
`FieldValue`s. The generated implementation uses the `ColumnDescriptor` flags to build its
statements:

- `ColumnDescriptor.id(...)` marks an auto-generated primary key. It is excluded from `INSERT`,
  and its value is read back from the generated keys.
- `ColumnDescriptor.idManual(...)` marks a primary key that the application supplies.
- `ColumnDescriptor.of(...)` marks a regular column.

## Using it in a modular project

The default mapper reads and writes private fields through
`MethodHandles.privateLookupIn()`. Your `module-info.java` must therefore open the entity
package and the repository package to the framework:

```java
module com.example.app {
    requires io.simpleton.persistence;
    requires java.sql;

    opens com.example.entity     to io.simpleton.persistence;
    opens com.example.repository to io.simpleton.persistence;
}
```

## Error handling

The generated methods wrap any `SQLException` in a `RuntimeException`. A `@Query` method that
returns `T` directly throws the unchecked `NotFoundException` when no row matches. Declare
`Optional<T>` instead to handle a missing row without an exception.

## Building

```bash
mvn clean install
```

This builds the processor and runs its unit tests, then compiles `test-app` with the processor
and runs the integration tests on an in-memory H2 database.

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
