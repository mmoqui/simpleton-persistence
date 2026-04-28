package io.simpleton.persistence.processor;

import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcRepositoryProcessorTest {

    @Test
    void shouldGenerateImplementationAndFactoryForAnnotatedRepository() throws Exception {
        Path workDir = Files.createTempDirectory("processor-test-");
        Path sourceDir = Files.createDirectories(workDir.resolve("src"));
        Path outputDir = Files.createDirectories(workDir.resolve("classes"));
        Path generatedDir = Files.createDirectories(workDir.resolve("generated"));

        Path entitySource = sourceDir.resolve("test/Person.java");
        Files.createDirectories(entitySource.getParent());
        Files.writeString(entitySource, """
                package test;

                public class Person {
                    private Long id;
                    private String name;

                    public Person() {
                    }
                }
                """);

        Path repositorySource = sourceDir.resolve("test/PersonRepository.java");
        Files.writeString(repositorySource, """
                package test;

                import io.simpleton.persistence.JdbcRepository;
                import io.simpleton.persistence.Repository;

                @JdbcRepository("people")
                public interface PersonRepository extends Repository<Person> {
                }
                """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDir));

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    List.of("-s", generatedDir.toString(), "-proc:only", "-classpath", System.getProperty("java.class.path")),
                    null,
                    fileManager.getJavaFileObjectsFromPaths(List.of(entitySource, repositorySource))
            );
            task.setProcessors(List.of(new JdbcRepositoryProcessor()));

            assertTrue(task.call(), "Compilation with processor should succeed");
        }

        Path implementation = generatedDir.resolve("test/PersonRepositoryImpl.java");
        Path factory = generatedDir.resolve("test/PersonRepositoryFactory.java");

        assertTrue(Files.exists(implementation), "Generated implementation should exist");
        assertTrue(Files.exists(factory), "Generated repository factory should exist");

        String implementationCode = Files.readString(implementation);
        assertTrue(implementationCode.contains("class PersonRepositoryImpl"));
        assertTrue(implementationCode.contains("TABLE = \"people\""));
        assertTrue(implementationCode.contains("MethodHandles.privateLookupIn"));

        String factoryCode = Files.readString(factory);
        assertTrue(factoryCode.contains("implements RepositoryFactory<PersonRepository>"));
        assertTrue(factoryCode.contains("return new PersonRepositoryImpl(dataSource);"));
    }

    @Test
    void shouldUseEntityNameAsTableWhenAnnotationValueIsEmpty() throws Exception {
        Path workDir = Files.createTempDirectory("processor-test-default-");
        Path sourceDir = Files.createDirectories(workDir.resolve("src"));
        Path outputDir = Files.createDirectories(workDir.resolve("classes"));
        Path generatedDir = Files.createDirectories(workDir.resolve("generated"));

        Path entitySource = sourceDir.resolve("demo/Book.java");
        Files.createDirectories(entitySource.getParent());
        Files.writeString(entitySource, """
                package demo;

                public class Book {
                    private Long id;
                    private String title;

                    public Book() {
                    }
                }
                """);

        Path repositorySource = sourceDir.resolve("demo/BookRepository.java");
        Files.writeString(repositorySource, """
                package demo;

                import io.simpleton.persistence.JdbcRepository;
                import io.simpleton.persistence.Repository;

                @JdbcRepository
                public interface BookRepository extends Repository<Book> {
                }
                """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDir));

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    List.of("-s", generatedDir.toString(), "-proc:only", "-classpath", System.getProperty("java.class.path")),
                    null,
                    fileManager.getJavaFileObjectsFromPaths(List.of(entitySource, repositorySource))
            );
            task.setProcessors(List.of(new JdbcRepositoryProcessor()));

            assertTrue(task.call(), "Compilation with processor should succeed");
        }

        String implementationCode = Files.readString(generatedDir.resolve("demo/BookRepositoryImpl.java"));
        assertTrue(implementationCode.contains("TABLE = \"Book\""));
        assertTrue(implementationCode.contains("SQL_INSERT = \"INSERT INTO Book (id, title) VALUES (?, ?)\""));

        long factoryFiles = Files.list(generatedDir.resolve("demo"))
                .filter(path -> path.getFileName().toString().endsWith("Factory.java"))
                .count();
        assertEquals(1, factoryFiles);
    }
}
