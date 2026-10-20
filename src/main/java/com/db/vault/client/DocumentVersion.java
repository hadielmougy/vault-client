package com.db.vault.client;

import java.time.Instant;
import java.util.Map;

/** A past version of a document: what the store knew right after that change. */
public record DocumentVersion(String documentId, int version, String state, String stateLabel, Instant createdAt,
                              Map<String, Object> content) implements FieldValues {}
