/// # Pricing rules
///
/// Prices are computed in three steps:
///
/// 1. every [line][Cart.Line] is priced on its own
/// 2. the lines are summed, see {@link Cart#total()}
/// 3. a {@link Pricing} may adjust the sum
///
/// | Step | Where |
/// |------|-------|
/// | line | {@link Cart.Line#subtotal()} |
/// | sum  | {@link Cart#total()} |
@DocPage(section = "Guides", order = 10, keywords = {"discount", "tax"})
package shop;

import dev.doctize.annotations.DocPage;
