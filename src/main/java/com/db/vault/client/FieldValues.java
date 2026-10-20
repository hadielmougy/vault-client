package com.db.vault.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Typed access to a document's field values. Values arrive as plain Java
 * types: {@link String}, {@link BigDecimal} (never double), {@link Boolean},
 * {@link Map} for embedded objects and references, or null when not set.
 */
public interface FieldValues {

    /** The value a sensitive field has when the caller may not read it. */
    String RESTRICTED = "[restricted]";

    Map<String, Object> content();

    default Object get(String field) {
        return content().get(field);
    }

    default boolean isSet(String field) {
        return get(field) != null;
    }

    /** True if the field is sensitive and the caller does not own it. */
    default boolean isRestricted(String field) {
        return RESTRICTED.equals(get(field));
    }

    default String string(String field) {
        Object v = get(field);
        return v == null ? null : v.toString();
    }

    default BigDecimal decimal(String field) {
        Object v = readable(field);
        return v == null ? null : (BigDecimal) v;
    }

    default Boolean bool(String field) {
        return (Boolean) readable(field);
    }

    default LocalDate date(String field) {
        Object v = readable(field);
        return v == null ? null : LocalDate.parse(v.toString());
    }

    /** A user or document reference, with the display snapshot. */
    default Ref ref(String field) {
        return Ref.fromValue(readable(field));
    }

    /** An embedded object (for example an address) as field name to value. */
    @SuppressWarnings("unchecked")
    default Map<String, Object> object(String field) {
        return (Map<String, Object>) readable(field);
    }

    private Object readable(String field) {
        if (isRestricted(field)) {
            throw new IllegalStateException("Field '" + field + "' is restricted for this caller");
        }
        return get(field);
    }
}
