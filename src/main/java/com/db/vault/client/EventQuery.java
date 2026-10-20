package com.db.vault.client;

/**
 * Filters for the event store. {@link VaultClient#events(EventQuery)} returns
 * events oldest first, after {@code afterId}, so it can be used to page forward.
 */
public record EventQuery(long afterId, String documentId, String actorId, String eventType, int limit) {

    public static EventQuery from(long afterId) {
        return new EventQuery(afterId, null, null, null, 200);
    }

    public EventQuery forDocument(String id) {
        return new EventQuery(afterId, id, actorId, eventType, limit);
    }

    public EventQuery byActor(String id) {
        return new EventQuery(afterId, documentId, id, eventType, limit);
    }

    public EventQuery ofEventType(String type) {
        return new EventQuery(afterId, documentId, actorId, type, limit);
    }

    public EventQuery limit(int max) {
        return new EventQuery(afterId, documentId, actorId, eventType, max);
    }

    EventQuery after(long id) {
        return new EventQuery(id, documentId, actorId, eventType, limit);
    }
}
