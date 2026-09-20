package shop.domain;
import jakarta.persistence.*;
import java.util.*;
@Entity public class Customer { @Id private long id; private String name; private String email; @OneToMany(mappedBy = "customer") private List<Order> orders; public String getName() { return name; } }
