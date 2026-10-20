package com.db.vault.client;

import java.util.Objects;
import java.util.function.Supplier;

/** How the client authenticates. Systems use an API key today; a token supplier is ready for SSO. */
public interface Credentials {

    /** Header name and value to send with every request. */
    String headerName();

    String headerValue();

    static Credentials apiKey(String key) {
        Objects.requireNonNull(key, "key");
        return new Credentials() {
            @Override
            public String headerName() {
                return "X-Api-Key";
            }

            @Override
            public String headerValue() {
                return key;
            }

            @Override
            public String toString() {
                return "ApiKey[****]";
            }
        };
    }

    /** A bearer token fetched for every request (for example from an OAuth token cache). */
    static Credentials bearer(Supplier<String> token) {
        Objects.requireNonNull(token, "token");
        return new Credentials() {
            @Override
            public String headerName() {
                return "Authorization";
            }

            @Override
            public String headerValue() {
                return "Bearer " + token.get();
            }

            @Override
            public String toString() {
                return "Bearer[****]";
            }
        };
    }
}
