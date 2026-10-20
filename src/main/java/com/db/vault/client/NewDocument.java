package com.db.vault.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A request to create a document of a type. Like {@link Proposal}, it carries an
 * idempotency key that survives retries.
 *
 * <pre>{@code
 * NewDocument deal = NewDocument.of("pipeline-deal")
 *         .set("clientCompanyName", "Example GmbH")
 *         .set("relationshipManager", Ref.user("anna"))
 *         .idempotencyKey("crm-opp-4711")
 *         .evidence("crm", "CRM-OPP-4711")
 *         .build();
 * }</pre>
 */
public final class NewDocument {

    private final String type;
    private final Map<String, Object> fields;
    private final String idempotencyKey;
    private final Map<String, Object> evidence;

    private NewDocument(Builder b) {
        this.type = b.type;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(b.fields));
        this.idempotencyKey = b.idempotencyKey != null ? b.idempotencyKey : UUID.randomUUID().toString();
        this.evidence = Collections.unmodifiableMap(new LinkedHashMap<>(b.evidence));
    }

    public static Builder of(String type) {
        return new Builder(type);
    }

    public String type() {
        return type;
    }

    public Map<String, Object> fields() {
        return fields;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public Map<String, Object> evidence() {
        return evidence;
    }

    public static final class Builder {
        private final String type;
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private final Map<String, Object> evidence = new LinkedHashMap<>();
        private String idempotencyKey;

        private Builder(String type) {
            this.type = Objects.requireNonNull(type, "type");
        }

        public Builder set(String field, Object value) {
            fields.put(Objects.requireNonNull(field, "field"), value);
            return this;
        }

        public Builder setAll(Map<String, ?> values) {
            values.forEach(this::set);
            return this;
        }

        public Builder idempotencyKey(String key) {
            this.idempotencyKey = key;
            return this;
        }

        public Builder evidence(String source, String reference) {
            evidence.put("source", source);
            if (reference != null) {
                evidence.put("reference", reference);
            }
            return this;
        }

        public NewDocument build() {
            return new NewDocument(this);
        }
    }
}
