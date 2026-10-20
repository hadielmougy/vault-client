package com.db.vault.client;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The current state of a document as the caller may see it: field values
 * (sensitive ones redacted unless owned), where each value came from, and
 * which transitions the caller could request right now.
 */
public record Document(
        String id,
        String type,
        String typeName,
        int typeVersion,
        String state,
        String stateLabel,
        boolean terminal,
        int version,
        String title,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> content,
        Map<String, Provenance> provenance,
        List<String> editableFields,
        List<TransitionInfo> transitions) implements FieldValues {

    /** Who last set a field, when, in which version, and with what evidence. */
    public record Provenance(long eventId, int version, String actorId, String actorKind, String actorDisplay,
                             Instant at, Map<String, Object> evidence) {}

    /**
     * A transition of the document's type, seen from the caller: whether it is
     * possible from the current state, allowed for the caller, and what is missing.
     */
    public record TransitionInfo(String name, String label, String to, String toLabel, boolean auto,
                                 boolean available, boolean allowed, List<String> requiredRoles,
                                 List<String> requires, List<String> missing, String blockedReason) {}

    public Optional<TransitionInfo> transition(String name) {
        return transitions.stream().filter(t -> t.name().equals(name)).findFirst();
    }

    /** Whether the caller could request this transition now (roles, state and required fields). */
    public boolean canRequest(String transition) {
        return transition(transition).map(TransitionInfo::allowed).orElse(false);
    }

    public boolean canEdit(String field) {
        return editableFields.contains(field);
    }
}
