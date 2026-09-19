package dev.doctize.model;

import dev.doctize.annotations.Doc;

/** A method or constructor parameter. */
@Doc(group = "Source model", order = 43)
public record Parameter(String name, String type) {}
