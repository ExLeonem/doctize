package shop;

import dev.doctize.annotations.Doc;

/**
 * Decides what a {@link Cart} costs.
 *
 * <p>Implementations must be free of side effects. See {@link Cart#total()} for the default behaviour.
 *
 * @apiNote Call it from one thread at a time.
 */
@Doc(section = "Reference", order = 6)
public interface Pricing {

    /** The multiplier applied to every price. */
    double FACTOR = 1.0;

    /**
     * Prices a cart.
     *
     * @param cart the cart, never {@code null}
     * @return the price to pay
     */
    Money price(Cart cart);

    /** Whether this pricing is applied before taxes. */
    default boolean beforeTaxes() {
        return true;
    }
}
