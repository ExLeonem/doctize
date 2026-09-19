package shop;

import dev.doctize.annotations.Doc;

/**
 * An amount of money in cents.
 *
 * @param cents the amount in the smallest unit, never negative
 * @since 1.0
 */
@Doc(section = "Reference", order = 5)
public record Money(long cents) {

    /** Adds two amounts. */
    public Money plus(Money other) {
        return new Money(cents + other.cents);
    }
}
