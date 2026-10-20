package com.db.vault.client;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One entry of the event store. {@code eventType} is DOCUMENT_CREATED,
 * FIELDS_UPDATED or STATE_CHANGED; the payload depends on it, and the helper
 * methods read it for you.
 */
public record Event(
        long id,
        String documentId,
        int version,
        String type,
        int typeVersion,
        String eventType,
        ActorInfo actor,
        Instant at,
        String proposalId,
        Map<String, Object> payload) {

    public static final String DOCUMENT_CREATED = "DOCUMENT_CREATED";
    public static final String FIELDS_UPDATED = "FIELDS_UPDATED";
    public static final String STATE_CHANGED = "STATE_CHANGED";

    /** A field value before and after a change. */
    public record Change(Object from, Object to) {}

    /** A state change; {@code derived} means the store fired it automatically. */
    public record StateChange(String transition, String from, String to, boolean derived) {}

    /** Field changes of a FIELDS_UPDATED event, or the initial values of DOCUMENT_CREATED. */
    @SuppressWarnings("unchecked")
    public Map<String, Change> changes() {
        Map<String, Change> result = new LinkedHashMap<>();
        if (FIELDS_UPDATED.equals(eventType) && payload.get("changes") instanceof Map<?, ?> changes) {
            ((Map<String, Object>) changes).forEach((field, diff) -> {
                Map<String, Object> d = (Map<String, Object>) diff;
                result.put(field, new Change(d.get("from"), d.get("to")));
            });
        } else if (DOCUMENT_CREATED.equals(eventType) && payload.get("fields") instanceof Map<?, ?> fields) {
            ((Map<String, Object>) fields).forEach((field, value) -> result.put(field, new Change(null, value)));
        }
        return result;
    }

    public Optional<StateChange> stateChange() {
        if (!STATE_CHANGED.equals(eventType)) {
            return Optional.empty();
        }
        return Optional.of(new StateChange((String) payload.get("transition"), (String) payload.get("from"),
                (String) payload.get("to"), Boolean.TRUE.equals(payload.get("derived"))));
    }

    /** The evidence the proposer attached (source, reference, ...), if any. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> evidence() {
        Object e = payload.get("evidence");
        return e instanceof Map<?, ?> ? (Map<String, Object>) e : Map.of();
    }
}
