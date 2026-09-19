package shop;

import dev.doctize.annotations.Doc;

/** The state of an order. */
@Doc(section = "Reference", order = 4)
public enum Status {

    /** Not paid yet. */
    OPEN,

    /** Paid and on its way. */
    SHIPPED
}
