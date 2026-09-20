package shop.service;
import shop.domain.Order;
public interface Repository<T> { void save(T entity); java.util.Optional<T> find(long id); }
