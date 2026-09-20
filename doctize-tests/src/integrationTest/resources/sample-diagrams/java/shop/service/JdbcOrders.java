package shop.service;
import shop.domain.Order;
public class JdbcOrders implements Repository<Order> { public void save(Order o) { log(o); } public java.util.Optional<Order> find(long id) { return java.util.Optional.empty(); } private void log(Order o) { } }
