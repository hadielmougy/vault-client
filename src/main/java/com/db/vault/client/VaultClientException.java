package com.db.vault.client;

/**
 * Base of all client errors. Thrown as is when the store could not be reached
 * (after retries); {@link VaultApiException} when the store answered with an error.
 */
public class VaultClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public VaultClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public VaultClientException(String message) {
        super(message);
    }
}
