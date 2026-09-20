package shop.service;
import dev.doctize.annotations.*;
import shop.domain.*;
public class OrderService {
    private final Repository<Order> orders = new JdbcOrders();
    private final Mailer mailer = new Mailer();
    @Flow(name = "Place an order", description = "What happens between the checkout button and the confirmation mail.")
    public Order place(Customer customer, java.util.List<Line> lines) {
        Order order = new Order("n-1", customer);
        lines.forEach(order::add);
        validate(order);
        orders.save(order);
        mailer.confirm(order);
        return order;
    }
    @Transition(from = "NEW", to = "PAID", on = "pay", machine = shop.state.OrderState.class) public void pay(Order o) { orders.save(o); }
    @Transition(from = {"NEW", "PAID"}, to = "CANCELLED", on = "cancel", machine = shop.state.OrderState.class) public void cancel(Order o) { orders.save(o); }
    private void validate(Order o) { if (o.total().signum() < 0) throw new IllegalStateException(); }
    @DiagramIgnore private void internalMetrics() { }
}
