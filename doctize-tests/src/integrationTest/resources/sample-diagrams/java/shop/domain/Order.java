package shop.domain;
import jakarta.persistence.*;
import java.util.*;
import shop.state.OrderState;
@Entity @Table(name = "orders")
public class Order extends Auditable implements Comparable<Order> {
    @Id private long id;
    private String number;
    private java.math.BigDecimal total;
    private OrderState state = OrderState.NEW;
    @ManyToOne(optional = false) @JoinColumn(name = "customer_id") private Customer customer;
    @OneToMany(mappedBy = "order") private List<Line> lines = new ArrayList<>();
    @ManyToMany private Set<Tag> tags = new HashSet<>();
    @Embedded private Address shipTo;
    @Transient private String cache;
    public static final int MAX_LINES = 100;
    public Order(String number, Customer customer) { this.number = number; this.customer = customer; }
    public void add(Line line) { lines.add(line); }
    public java.math.BigDecimal total() { return lines.stream().map(Line::price).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add); }
    public OrderState state() { return state; }
    public long getId() { return id; }
    @Override public int compareTo(Order o) { return number.compareTo(o.number); }
    public static class Draft { private String note; public Order build(Customer c) { return new Order("draft", c); } }
}
