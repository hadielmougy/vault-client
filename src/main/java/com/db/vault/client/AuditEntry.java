package com.db.vault.client;

import java.time.Instant;

/**
 * One proposal from the audit, including rejected ones. {@code outcome} is
 * ACCEPTED, NO_CHANGE or REJECTED; rejected entries carry the reason.
 */
public record AuditEntry(String proposalId, String kind, String outcome, String documentId, ActorInfo actor,
                         String reasonCode, String reasonMessage, Integer resultVersion, Instant at) {

    public boolean rejected() {
        return "REJECTED".equals(outcome);
    }
}
