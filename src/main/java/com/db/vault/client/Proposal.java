package com.db.vault.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A proposed change to a document: field values, a transition, or both, applied
 * atomically by the store. Every proposal gets an idempotency key when built,
 * and keeps it across retries, so sending it twice never applies it twice.
 *
 * <pre>{@code
 * Proposal p = Proposal.builder()
 *         .set("creditRating", "A")
 *         .transition("approve")
 *         .evidence("credit-engine", "RATING-9020")
 *         .build();
 * }</pre>
 */
public final class Proposal {

    private final Map<String, Object> changes;
    private final String transition;
    private final Integer baseVersion;
    private final String idempotencyKey;
    private final Map<String, Object> evidence;

    private Proposal(Builder b) {
        this.changes = Collections.unmodifiableMap(new LinkedHashMap<>(b.changes));
        this.transition = b.transition;
        this.baseVersion = b.baseVersion;
        this.idempotencyKey = b.idempotencyKey != null ? b.idempotencyKey : UUID.randomUUID().toString();
        this.evidence = Collections.unmodifiableMap(new LinkedHashMap<>(b.evidence));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> changes() {
        return changes;
    }

    public String transition() {
        return transition;
    }

    public Integer baseVersion() {
        return baseVersion;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Map<String, Object> evidence() {
        return evidence;
    }

    public static final class Builder {
        private final Map<String, Object> changes = new LinkedHashMap<>();
        private final Map<String, Object> evidence = new LinkedHashMap<>();
        private String transition;
        private Integer baseVersion;
        private String idempotencyKey;

        private Builder() {}

        /**
         * Sets a field. Values: String, numbers (BigDecimal recommended for amounts),
         * Boolean, LocalDate, {@link Ref} for references, Map for embedded objects.
         */
        public Builder set(String field, Object value) {
            changes.put(Objects.requireNonNull(field, "field"), value);
            return this;
        }

        public Builder setAll(Map<String, ?> values) {
            values.forEach(this::set);
            return this;
        }

        /** Clears a field (sets it to "not set"). */
        public Builder clear(String field) {
            return set(field, null);
        }

        /** Requests a transition, checked after the field changes of this proposal are applied. */
        public Builder transition(String name) {
            this.transition = name;
            return this;
        }

        /**
         * Only apply if the document is still at this version (optimistic
         * concurrency). Otherwise the store rejects with STALE_VERSION.
         */
        public Builder baseVersion(int version) {
            this.baseVersion = version;
            return this;
        }

        /**
         * Use your own key, for example the id of the event or workflow step that
         * produced this proposal, so redelivery of that event is also deduplicated.
         */
        public Builder idempotencyKey(String key) {
            this.idempotencyKey = key;
            return this;
        }

        /** Where the values came from; stored with the event and shown as provenance. */
        public Builder evidence(String source, String reference) {
            evidence.put("source", source);
            if (reference != null) {
                evidence.put("reference", reference);
            }
            return this;
        }

        public Builder evidence(String key, Object value) {
            evidence.put(key, value);
            return this;
        }

        public Proposal build() {
            if (changes.isEmpty() && transition == null) {
                throw new IllegalStateException("A proposal needs field changes, a transition, or both");
            }
            return new Proposal(this);
        }
    }
}
