package com.db.vault.client;

import java.util.List;
import java.util.Map;

/**
 * The store rejected a request. {@link #code()} is stable and meant for
 * program logic (see {@link ErrorCodes}); the message is for people.
 *
 * <pre>{@code
 * try {
 *     client.propose(id, proposal);
 * } catch (VaultApiException e) {
 *     if (e.is(ErrorCodes.GUARD_FAILED)) {
 *         log.info("Still missing: {}", e.missingFields());
 *     }
 * }
 * }</pre>
 */
public class VaultApiException extends VaultClientException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient Map<String, Object> details;

    public VaultApiException(int status, String code, String message, Map<String, Object> details) {
        super(status + " " + code + ": " + message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : details;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }

    public boolean is(String errorCode) {
        return errorCode.equals(code);
    }

    /** The audit id of the rejected proposal, when the request was a proposal. */
    public String proposalId() {
        Object id = details.get("proposalId");
        return id == null ? null : id.toString();
    }

    /** For GUARD_FAILED: the fields the transition still needs. */
    @SuppressWarnings("unchecked")
    public List<String> missingFields() {
        Object missing = details.get("missing");
        return missing instanceof List<?> ? (List<String>) missing : List.of();
    }

    /** For INVALID_VALUE: the problem per field. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fieldProblems() {
        Object fields = details.get("fields");
        return fields instanceof Map<?, ?> ? (Map<String, Object>) fields : Map.of();
    }

    /** For STALE_VERSION: the version the document has now. */
    public Integer currentVersion() {
        Object v = details.get("currentVersion");
        return v instanceof Number n ? n.intValue() : null;
    }
}
