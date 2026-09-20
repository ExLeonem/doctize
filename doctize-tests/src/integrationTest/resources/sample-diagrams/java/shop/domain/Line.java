package shop.domain;
import jakarta.persistence.*;
@Entity public class Line { @Id private long id; @ManyToOne private Order order; @ManyToOne private Product product; private int quantity; public java.math.BigDecimal price() { return product.price().multiply(java.math.BigDecimal.valueOf(quantity)); } }
