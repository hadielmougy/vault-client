package com.db.vault.client;

import java.util.Map;
import java.util.Objects;

/**
 * A reference to a user or another document. When sending, only {@code ref}
 * matters; when reading, {@code display} is the snapshot the store took when
 * the reference was set (for example the user's name at that time).
 */
public record Ref(String ref, String display) {

    public Ref {
        Objects.requireNonNull(ref, "ref");
    }

    /** A user, by username ("anna") or id ("user:anna"). */
    public static Ref user(String usernameOrId) {
        return new Ref(usernameOrId.contains(":") ? usernameOrId : "user:" + usernameOrId, null);
    }

    /** Another document, by id ("LOAN-1001"). */
    public static Ref document(String documentId) {
        return new Ref(documentId, null);
    }

    static Ref fromValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.get("ref") != null) {
            Object display = map.get("display");
            return new Ref(map.get("ref").toString(), display == null ? null : display.toString());
        }
        throw new IllegalStateException("Not a reference: " + value);
    }
}
