package com.db.vault.client;

/** Error codes the store returns, for use with {@link VaultApiException#is(String)}. */
public final class ErrorCodes {

    /** 401: missing or unknown API key, or the system is inactive. */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INVALID_API_KEY = "INVALID_API_KEY";
    /** 403: the caller does not own one of the fields it tried to change. */
    public static final String NOT_FIELD_OWNER = "NOT_FIELD_OWNER";
    /** 403: the caller's roles may not request this transition. */
    public static final String TRANSITION_NOT_PERMITTED = "TRANSITION_NOT_PERMITTED";
    /** 403: the caller's roles may not create documents of this type. */
    public static final String NOT_ALLOWED_TO_CREATE = "NOT_ALLOWED_TO_CREATE";
    /** 409: the transition needs fields that are not filled; see missingFields(). */
    public static final String GUARD_FAILED = "GUARD_FAILED";
    /** 409: the transition is not possible from the current state. */
    public static final String TRANSITION_NOT_AVAILABLE = "TRANSITION_NOT_AVAILABLE";
    /** 409: the document is in a final state. */
    public static final String DOCUMENT_CLOSED = "DOCUMENT_CLOSED";
    /** 409: baseVersion is behind the document; see currentVersion(). */
    public static final String STALE_VERSION = "STALE_VERSION";
    /** 400: a value does not fit its field; see fieldProblems(). */
    public static final String INVALID_VALUE = "INVALID_VALUE";
    public static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";
    public static final String UNKNOWN_TRANSITION = "UNKNOWN_TRANSITION";
    /** 400: automatic transitions fire by themselves and cannot be requested. */
    public static final String AUTOMATIC_TRANSITION = "AUTOMATIC_TRANSITION";
    public static final String NOT_FOUND = "NOT_FOUND";

    private ErrorCodes() {}
}
