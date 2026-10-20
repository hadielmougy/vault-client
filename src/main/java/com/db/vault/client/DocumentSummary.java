package com.db.vault.client;

import java.time.Instant;

/** One row of a document listing. */
public record DocumentSummary(String id, String type, String typeName, int typeVersion, String state, String stateLabel,
                              boolean terminal, int version, String title, Instant updatedAt) {}
