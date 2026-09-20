package shop.service;
import shop.domain.*;
public class Mailer { public void confirm(Order order) { render(order); deliver(order.total().toString()); } private void render(Order o) { } private void deliver(String text) { } }
