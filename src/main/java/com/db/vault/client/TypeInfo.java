package com.db.vault.client;

import java.util.Map;

/** A published document type version. {@code definition} is the type's JSON definition as maps and lists. */
public record TypeInfo(String key, String name, int version, boolean canCreate, Map<String, Object> definition) {}
