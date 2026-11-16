package io.simpleton.persistence.test.repository;

import io.simpleton.persistence.annotation.JdbcRepository;
import io.simpleton.persistence.annotation.Repository;
import io.simpleton.persistence.test.entity.Product;
import io.simpleton.persistence.test.mapper.ProductMapper;

/**
 * Repository for {@link Product} entities — <b>custom mapper case</b>.
 *
 * <p>The field names of {@code Product} differ from the SQL column names.
 * {@link ProductMapper} provides the explicit mapping. The processor injects
 * {@code new ProductMapper()} into {@code ProductRepositoryFactory}.</p>
 *
 * <p>Classes generated at compile time:</p>
 * <ul>
 *   <li>{@code ProductRepositoryImpl} — JDBC implementation</li>
 *   <li>{@code ProductRepositoryFactory} — factory with {@code ProductMapper}</li>
 * </ul>
 */
@JdbcRepository(table = "products", mapper = ProductMapper.class)
public interface ProductRepository extends Repository<Product, Long> {
}
