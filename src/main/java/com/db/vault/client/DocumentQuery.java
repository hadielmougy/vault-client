package com.db.vault.client;

/** Filters for listing documents. Start with {@link #all()} or {@link #ofType(String)}. */
public record DocumentQuery(String type, String state, String search, Integer limit) {

    public static DocumentQuery all() {
        return new DocumentQuery(null, null, null, null);
    }

    public static DocumentQuery ofType(String type) {
        return new DocumentQuery(type, null, null, null);
    }

    public DocumentQuery inState(String state) {
        return new DocumentQuery(type, state, search, limit);
    }

    /** Matches the document id or title. */
    public DocumentQuery search(String text) {
        return new DocumentQuery(type, state, text, limit);
    }

    public DocumentQuery limit(int max) {
        return new DocumentQuery(type, state, search, max);
    }
}
