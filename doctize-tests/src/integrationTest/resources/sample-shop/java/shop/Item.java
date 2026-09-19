package shop;

import dev.doctize.annotations.Doc;

/// Something that can be bought.
///
/// @param sku the stock keeping unit, unique per product
/// @param price the price of one piece
@Doc(section = "Reference", order = 3)
public record Item(String sku, Money price) {}
