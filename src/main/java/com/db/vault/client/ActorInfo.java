package com.db.vault.client;

import java.util.List;

/** A user or system as the store reports it. {@code kind} is "USER" or "SYSTEM". */
public record ActorInfo(String id, String kind, String displayName, List<String> roles) {

    public boolean isSystem() {
        return "SYSTEM".equals(kind);
    }
}
