package dev.doctize.model;

import dev.doctize.annotations.Doc;

/** How a source element takes part in the documentation. */
@Doc(group = "Source model", order = 45)
public enum Role {
    /** A type documented as an API reference page ({@code @Doc}). */
    API,
    /** An element whose comment is a free-form guide page ({@code @DocPage}). */
    PAGE
}
