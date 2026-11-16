package io.github.jdbcprocessor.test.entity;

/**
 * Product entity — <b>custom mapper case</b>.
 *
 * <p>Java field names differ from SQL column names:</p>
 * <pre>
 * Java field   SQL column
 * ----------   ----------
 * id           id
 * label        product_label
 * unitPrice    unit_price
 * stockQty     stock_qty
 * </pre>
 *
 * <p>A {@link io.github.jdbcprocessor.test.mapper.ProductMapper} handles the
 * mapping. This entity has no knowledge of that mapper.</p>
 *
 * <pre>
 * CREATE TABLE products (
 *     id            BIGINT AUTO_INCREMENT PRIMARY KEY,
 *     product_label VARCHAR(200)     NOT NULL,
 *     unit_price    DOUBLE PRECISION NOT NULL,
 *     stock_qty     INT              NOT NULL DEFAULT 0
 * );
 * </pre>
 */
public class Product {

    private Long    id;
    private String  label;
    private Double  unitPrice;
    private Integer stockQty;

    public Product() {}

    public Product(String label, Double unitPrice, Integer stockQty) {
        this.label     = label;
        this.unitPrice = unitPrice;
        this.stockQty  = stockQty;
    }

    public Long    getId()        { return id; }
    public String  getLabel()     { return label; }
    public Double  getUnitPrice() { return unitPrice; }
    public Integer getStockQty()  { return stockQty; }

    public void setId(Long id)          { this.id = id; }
    public void setLabel(String l)      { this.label = l; }
    public void setUnitPrice(Double p)  { this.unitPrice = p; }
    public void setStockQty(Integer q)  { this.stockQty = q; }

    @Override public String toString() {
        return "Product{id=" + id + ", label='" + label +
               "', unitPrice=" + unitPrice + ", stockQty=" + stockQty + '}';
    }
}
