package shop.domain;
import jakarta.persistence.*;
@Entity public class Product { @Id private String sku; private String title; private java.math.BigDecimal price; public java.math.BigDecimal price() { return price; } }
