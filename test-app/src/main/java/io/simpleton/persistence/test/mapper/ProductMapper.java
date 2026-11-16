package io.simpleton.persistence.test.mapper;

import io.simpleton.persistence.annotation.ColumnDescriptor;
import io.simpleton.persistence.annotation.FieldValue;
import io.simpleton.persistence.mapper.EntityMapper;
import io.simpleton.persistence.test.entity.Product;

import java.util.List;
import java.util.Map;

/**
 * Custom mapper for {@link Product}.
 *
 * <p>Declares the explicit mapping between Java field names and SQL column names:</p>
 *
 * <table>
 *   <tr><th>Java field</th><th>SQL column</th></tr>
 *   <tr><td>id</td><td>id</td></tr>
 *   <tr><td>label</td><td>product_label</td></tr>
 *   <tr><td>unitPrice</td><td>unit_price</td></tr>
 *   <tr><td>stockQty</td><td>stock_qty</td></tr>
 * </table>
 *
 * <p>This mapper has no knowledge of JDBC. It works purely with a
 * {@code Map<String, Object>} row (coming from any back-end) and returns
 * a {@code List<FieldValue>} that the back-end can bind to any query
 * mechanism it chooses.</p>
 *
 * <p>{@link Product} has no knowledge of this mapper and does not depend on it.</p>
 */
public final class ProductMapper implements EntityMapper<Product> {

  private static final List<ColumnDescriptor> COLUMNS = List.of(
          ColumnDescriptor.id("id",        "id"),
          ColumnDescriptor.of("label",     "product_label"),
          ColumnDescriptor.of("unitPrice", "unit_price"),
          ColumnDescriptor.of("stockQty",  "stock_qty")
  );

  @Override
  public List<ColumnDescriptor> columns() {
    return COLUMNS;
  }

  /**
   * Builds a {@link Product} from a generic row map.
   * Keys are SQL column names (lower-cased); values come from the back-end.
   */
  @Override
  public Product toEntity(Map<String, Object> row) {
    Product p = new Product();
    p.setId(       (Long)    row.get("id"));
    p.setLabel(    (String)  row.get("product_label"));
    p.setUnitPrice((Double)  row.get("unit_price"));
    p.setStockQty( (Integer) row.get("stock_qty"));
    return p;
  }

  /**
   * Returns all field values from the entity, paired with their descriptors.
   * The back-end filters on {@link ColumnDescriptor#generated()} and
   * {@link ColumnDescriptor#id()} flags to build the right statement.
   */
  @Override
  public List<FieldValue> toFieldValues(Product p) {
    return List.of(
        FieldValue.of(COLUMNS.get(0), p.getId()),
        FieldValue.of(COLUMNS.get(1), p.getLabel()),
        FieldValue.of(COLUMNS.get(2), p.getUnitPrice()),
        FieldValue.of(COLUMNS.get(3), p.getStockQty())
    );
  }
}
