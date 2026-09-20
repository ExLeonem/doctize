package shop.web;
import shop.service.OrderService;
public class OrderController { private final OrderService service = new OrderService(); public void checkout() { service.place(null, java.util.List.of()); } }
