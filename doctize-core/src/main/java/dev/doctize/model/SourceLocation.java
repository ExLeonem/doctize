package dev.doctize.model;

import dev.doctize.annotations.Doc;

/**
 * Where an element is declared, so that its page can link to the source in the repository.
 *
 * @param path the source file, relative to the root of the Git repository, with {@code /} as separator
 * @param line the line the declaration starts on, counting from 1
 */
@Doc(group = "Source model", order = 47)
public record SourceLocation(String path, int line) {}
