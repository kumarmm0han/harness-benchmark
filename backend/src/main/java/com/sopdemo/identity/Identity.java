package com.sopdemo.identity;

/**
 * Fixed local demo identity (FR-001, DR-003). This is a labeled demo mechanism,
 * not production authentication. Roles:
 * <ul>
 *   <li>{@code AUTHOR} - saves drafts, validates, publishes, reads history</li>
 *   <li>{@code CONSUMER} - reads published content only</li>
 * </ul>
 */
public record Identity(String name, Role role) {

    public enum Role {
        AUTHOR,
        CONSUMER
    }

    public boolean isAuthor() {
        return role == Role.AUTHOR;
    }

    public boolean isConsumer() {
        return role == Role.CONSUMER;
    }
}
