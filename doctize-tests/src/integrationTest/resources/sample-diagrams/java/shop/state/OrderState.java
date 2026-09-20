package shop.state;
import dev.doctize.annotations.*;
@StateMachine(name = "Order lifecycle", initial = "NEW", terminal = {"SHIPPED", "CANCELLED"}, description = "What can happen to an order, from the cart to the door.")
public enum OrderState {
    @Transition(to = "PAID", on = "pay") @Transition(to = "CANCELLED", on = "cancel") NEW,
    @Transition(to = "PICKED", on = "pick") @Transition(to = "CANCELLED", on = "refund") PAID,
    @Transition(to = "SHIPPED", on = "ship") PICKED,
    SHIPPED,
    CANCELLED
}
