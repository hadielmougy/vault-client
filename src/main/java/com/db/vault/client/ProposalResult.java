package com.db.vault.client;

/**
 * The store's answer to an accepted proposal. Rejections are thrown as
 * {@link VaultApiException} instead.
 */
public record ProposalResult(Outcome outcome, String proposalId, Document document) {

    public enum Outcome {
        /** Applied: the document has a new version. */
        ACCEPTED,
        /** Valid, but the values were already as proposed: no new version. */
        NO_CHANGE,
        /**
         * Same idempotency key as an earlier proposal: nothing was applied again,
         * the document is returned as it is. Treat it like success; it typically
         * means an earlier attempt succeeded but its response was lost.
         */
        DUPLICATE
    }

    /** True if this call created a new version (false for NO_CHANGE and DUPLICATE). */
    public boolean changedDocument() {
        return outcome == Outcome.ACCEPTED;
    }
}
