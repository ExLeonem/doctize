---
title: Start shopping
order: 1
description: Put something in a cart.
keywords: [tutorial]
---

# Start shopping

Install the library first, see [Installation](../setup/install.md).

## Your first cart

```java title="Main.java"
var cart = Cart.empty().add(item);
```

Call {@link Cart#add(Item)} to add one piece, or {@link Cart#add(Item, int)} for several.
The result of {@link Cart#total()} is a [Money] value. See also [the pricing rules](../guides/pricing-rules.html).

![A cart](../images/cart.svg)

## Install

```groovy tab="Gradle"
implementation 'shop:shop:1.0'
```

```xml tab="Maven"
<dependency><groupId>shop</groupId></dependency>
```
