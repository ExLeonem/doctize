package shop;

import dev.doctize.annotations.Doc;
import dev.doctize.annotations.DocIgnore;
import java.util.ArrayList;
import java.util.List;

/// A shopping cart.
///
/// Create one with [Cart#empty()], fill it with {@link #add(Item)} and ask for the {@link #total()}:
///
/// ```java
/// Cart cart = Cart.empty().add(new Item("A-1", new Money(250)), 2);
/// ```
///
/// > [!TIP]
/// > A cart never contains the same item twice; adding it again raises the quantity.
///
/// @since 1.0
/// @see Item
@Doc(section = "Reference", order = 1, keywords = {"basket", "checkout"})
public final class Cart {

    private final List<Line> lines = new ArrayList<>();

    private Cart() {}

    /** One line of the cart. */
    @Doc(title = "Cart.Line", section = "Reference", order = 2)
    public record Line(Item item, int quantity) {

        /** The price of the whole line. */
        public Money subtotal() {
            return new Money(item.price().cents() * quantity);
        }
    }

    /**
     * Creates an empty cart.
     *
     * @return a new cart
     */
    public static Cart empty() {
        return new Cart();
    }

    /**
     * Adds one piece of an item.
     *
     * @param item the item to add
     * @return this cart
     */
    public Cart add(Item item) {
        return add(item, 1);
    }

    /**
     * Adds several pieces of an item.
     *
     * @param item the item to add
     * @param quantity how many pieces, at least one
     * @return this cart
     * @throws IllegalArgumentException if {@code quantity} is less than one
     */
    public Cart add(Item item, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity");
        }
        lines.add(new Line(item, quantity));
        return this;
    }

    /**
     * Adds many items at once.
     *
     * @param items the items
     * @return this cart
     * @deprecated use {@link #add(Item)} in a loop; this method will be removed in 2.0
     */
    @Deprecated(since = "1.1", forRemoval = true)
    public Cart addAll(Item... items) {
        for (Item item : items) {
            add(item);
        }
        return this;
    }

    /**
     * The sum of all lines.
     *
     * @return the total price
     */
    public Money total() {
        return lines.stream().map(Line::subtotal).reduce(new Money(0), Money::plus);
    }

    /** The lines of the cart, in the order they were added. */
    public List<Line> lines() {
        return List.copyOf(lines);
    }

    /** Not part of the public documentation. */
    @DocIgnore
    public void rebuildIndex() {}
}
