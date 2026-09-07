package com.sopdemo.web.identity;

/** The authenticated demo identity for the current request (ARC-002, FR-001). */
public record AuthenticatedUser(String id, boolean author) {}
