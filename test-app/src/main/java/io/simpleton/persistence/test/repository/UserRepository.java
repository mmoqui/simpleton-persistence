package io.simpleton.persistence.test.repository;

import io.simpleton.persistence.annotation.JdbcRepository;
import io.simpleton.persistence.annotation.Query;
import io.simpleton.persistence.annotation.Repository;
import io.simpleton.persistence.test.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link User} entities — convention case.
 *
 * <p>Demonstrates all four {@code @Query} return-type variants:</p>
 * <ul>
 *   <li>{@code List<T>} — {@link #findByAgeGreaterThan(int)}</li>
 *   <li>{@code Set<T>} — {@link #findByEmail(String)} (unlikely duplicate, but valid)</li>
 *   <li>{@code Optional<T>} — {@link #findByUsername(String)}</li>
 *   <li>{@code T} directly — {@link #getByUsername(String)}: throws
 *       {@link io.simpleton.persistence.annotation.NotFoundException} when absent</li>
 * </ul>
 */
@JdbcRepository(table = "users")
public interface UserRepository extends Repository<User, Long> {

    /** Returns all users whose age is strictly greater than {@code minAge}. */
    @Query("SELECT u FROM users u WHERE u.age > :minAge")
    List<User> findByAgeGreaterThan(int minAge);

    /**
     * Returns users matching the given username.
     * Returns an empty {@code Optional} when not found.
     */
    @Query("SELECT u FROM users u WHERE u.username = :username")
    Optional<User> findByUsername(String username);

    /**
     * Returns the user with the given username.
     * Throws {@link io.simpleton.persistence.annotation.NotFoundException}
     * when no user matches.
     */
    @Query("SELECT u FROM users u WHERE u.username = :username")
    User getByUsername(String username);

    /**
     * Returns users matching the given username prefix and minimum age,
     * demonstrating multi-parameter queries.
     */
    @Query("SELECT u FROM users u WHERE u.username LIKE :pattern AND u.age >= :minAge")
    List<User> findByUsernamePatternAndMinAge(String pattern, int minAge);
}
