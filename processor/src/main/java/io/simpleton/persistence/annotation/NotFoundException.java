package io.simpleton.persistence.annotation;

/**
 * Thrown by a generated repository method annotated with {@link Query} when the
 * return type is a plain entity type {@code T} (not {@code Optional<T>}) and the
 * query returns no row.
 *
 * <p>This is an unchecked exception: callers that need to handle the absence of a
 * result should declare the return type as {@code Optional<T>} instead.</p>
 *
 * <pre>{@code
 * // Throws NotFoundException when no user has that email
 * User user = repo.findByEmail("alice@example.com");
 *
 * // Safe alternative — use Optional
 * Optional<User> user = repo.findByEmail("alice@example.com");
 * }</pre>
 */
public class NotFoundException extends RuntimeException {

  /**
   * Constructs a new {@code NotFoundException} with a descriptive message.
   *
   * @param message human-readable description identifying the query and parameters
   */
  public NotFoundException(String message) {
    super(message);
  }
}
