# simpleton-persistence

Java 21 JDBC persistence framework powered by annotation processing.

## Features

- Generic `Repository<T>` contract with standard CRUD methods.
- `@JdbcRepository` annotation for developer-defined repository interfaces.
- Compile-time generation of `<RepositoryInterface>Impl` classes.
- Compile-time generation of `<RepositoryInterface>Factory` classes implementing `RepositoryFactory<R>`.
- Optional runtime fallback via `GenericRepositoryFactory`.
- Convention-based mapping:
  - SQL table name = `@JdbcRepository("...")` value or entity simple name.
  - SQL columns = entity field names.
  - Primary key field = `id`.
- Entity field access uses `MethodHandle` in generated implementations.

## Requirements

- Java 21
- Maven 3.9+

## Example

```java
import io.simpleton.persistence.JdbcRepository;
import io.simpleton.persistence.Repository;

@JdbcRepository("people")
public interface PersonRepository extends Repository<Person> {
}
```

Generated artifacts:

- `PersonRepositoryImpl`
- `PersonRepositoryFactory`

Runtime fallback:

```java
PersonRepository repository = GenericRepositoryFactory.create(PersonRepository.class, dataSource);
```

## Build

```bash
mvn clean test
```
