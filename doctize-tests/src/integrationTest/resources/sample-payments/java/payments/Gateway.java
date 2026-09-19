package payments;

import dev.doctize.annotations.Doc;

/**
 * Charges a customer. Works with the {@link shop.Cart} of the shop module.
 *
 * @since 2.0
 */
@Doc(section = "Reference", order = 1, keywords = {"payment"})
public interface Gateway {

    /**
     * Charges the total of a cart.
     *
     * @param cart the cart to charge, see {@link shop.Cart#total()}
     * @return {@code true} if the payment succeeded
     */
    boolean charge(Object cart);
}
