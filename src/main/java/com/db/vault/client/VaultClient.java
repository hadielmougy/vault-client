package com.db.vault.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Client for the document store. Thread-safe; create one per application and
 * share it.
 *
 * <pre>{@code
 * VaultClient store = VaultClient.builder()
 *         .baseUrl("https://vault.example")
 *         .apiKey(System.getenv("VAULT_API_KEY"))
 *         .build();
 *
 * Document deal = store.create(NewDocument.of("pipeline-deal")
 *         .set("clientCompanyName", "Example GmbH")
 *         .set("relationshipManager", Ref.user("anna"))
 *         .idempotencyKey("crm-opp-4711")
 *         .build()).document();
 *
 * store.propose(deal.id(), Proposal.builder()
 *         .set("negativeInformationFlag", false)
 *         .evidence("kyc-service", "KYC-123")
 *         .build());
 * }</pre>
 *
 * Rejections by the store are thrown as {@link VaultApiException} with a
 * stable {@link VaultApiException#code() code}; network failures (after
 * retries) as {@link VaultClientException}.
 */
public final class VaultClient {

    private final Transport transport;

    private VaultClient(Transport transport) {
        this.transport = transport;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The actor behind this client's credentials, with its roles. */
    public ActorInfo whoAmI() {
        return Mapping.actor(transport.get("/api/me"));
    }

    // ------------------------------------------------------------ writes

    /** Creates a document. A retried or repeated request with the same key returns DUPLICATE. */
    public ProposalResult create(NewDocument document) {
        ObjectNode body = Mapping.JSON.createObjectNode();
        body.put("type", document.type());
        body.set("fields", Mapping.toJson(document.fields()));
        body.put("idempotencyKey", document.idempotencyKey());
        if (!document.evidence().isEmpty()) {
            body.set("evidence", Mapping.toJson(document.evidence()));
        }
        return Mapping.result(transport.post("/api/documents", body));
    }

    /** Proposes field changes and/or a transition, applied atomically. */
    public ProposalResult propose(String documentId, Proposal proposal) {
        ObjectNode body = Mapping.JSON.createObjectNode();
        if (!proposal.changes().isEmpty()) {
            body.set("changes", Mapping.toJson(proposal.changes()));
        }
        if (proposal.transition() != null) {
            body.put("transition", proposal.transition());
        }
        if (proposal.baseVersion() != null) {
            body.put("baseVersion", proposal.baseVersion());
        }
        body.put("idempotencyKey", proposal.idempotencyKey());
        if (!proposal.evidence().isEmpty()) {
            body.set("evidence", Mapping.toJson(proposal.evidence()));
        }
        return Mapping.result(transport.post("/api/documents/" + Transport.segment(documentId) + "/proposals", body));
    }

    // ------------------------------------------------------------ reads

    public Document get(String documentId) {
        return Mapping.document(transport.get("/api/documents/" + Transport.segment(documentId)));
    }

    /** Like {@link #get(String)}, but empty instead of an exception when the document does not exist. */
    public Optional<Document> find(String documentId) {
        try {
            return Optional.of(get(documentId));
        } catch (VaultApiException e) {
            if (e.status() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    public List<DocumentSummary> list(DocumentQuery query) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", query.type());
        params.put("state", query.state());
        params.put("q", query.search());
        params.put("limit", query.limit());
        List<DocumentSummary> result = new ArrayList<>();
        transport.get("/api/documents" + Transport.query(params)).forEach(n -> result.add(Mapping.summary(n)));
        return result;
    }

    /** A past version: what the store knew right after that change. */
    public DocumentVersion version(String documentId, int version) {
        return Mapping.version(transport.get("/api/documents/" + Transport.segment(documentId) + "/versions/" + version));
    }

    /** All events of one document, oldest first. */
    public List<Event> history(String documentId) {
        List<Event> result = new ArrayList<>();
        transport.get("/api/documents/" + Transport.segment(documentId) + "/events").forEach(n -> result.add(Mapping.event(n)));
        return result;
    }

    /**
     * Events after {@code query.afterId()}, oldest first, at most {@code limit}.
     * Call again with the last id to page forward without gaps, or use
     * {@link #subscribe()} to follow the store continuously.
     */
    public List<Event> events(EventQuery query) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("afterId", query.afterId());
        params.put("documentId", query.documentId());
        params.put("actorId", query.actorId());
        params.put("eventType", query.eventType());
        params.put("limit", query.limit());
        params.put("order", "asc");
        List<Event> result = new ArrayList<>();
        transport.get("/api/events" + Transport.query(params)).forEach(n -> result.add(Mapping.event(n)));
        return result;
    }

    /** Every proposal for a document, newest first, including rejected ones. */
    public List<AuditEntry> audit(String documentId) {
        List<AuditEntry> result = new ArrayList<>();
        transport.get("/api/proposals" + Transport.query(Map.of("documentId", documentId, "limit", 500)))
                .forEach(n -> result.add(Mapping.audit(n)));
        return result;
    }

    /** The latest version of a type. */
    public TypeInfo type(String key) {
        return Mapping.type(transport.get("/api/types/" + Transport.segment(key)));
    }

    public TypeInfo type(String key, int version) {
        return Mapping.type(transport.get("/api/types/" + Transport.segment(key) + "?version=" + version));
    }

    /** Follows the event store in the background. See {@link EventSubscription}. */
    public EventSubscription.Builder subscribe() {
        return new EventSubscription.Builder(this);
    }

    // ------------------------------------------------------------ builder

    public static final class Builder {
        private String baseUrl;
        private Credentials credentials;
        private Duration timeout = Duration.ofSeconds(10);
        private RetryPolicy retryPolicy = RetryPolicy.defaults();
        private HttpClient httpClient;

        private Builder() {}

        public Builder baseUrl(String url) {
            this.baseUrl = url;
            return this;
        }

        public Builder apiKey(String key) {
            this.credentials = Credentials.apiKey(key);
            return this;
        }

        public Builder credentials(Credentials credentials) {
            this.credentials = credentials;
            return this;
        }

        /** Per-request timeout (default 10 seconds). */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder retryPolicy(RetryPolicy policy) {
            this.retryPolicy = policy;
            return this;
        }

        /** Your own HttpClient, for example with a proxy or a custom SSLContext. */
        public Builder httpClient(HttpClient client) {
            this.httpClient = client;
            return this;
        }

        public VaultClient build() {
            Objects.requireNonNull(baseUrl, "baseUrl is required");
            Objects.requireNonNull(credentials, "apiKey or credentials are required");
            HttpClient http = httpClient != null ? httpClient
                    : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            return new VaultClient(new Transport(http, baseUrl, credentials, retryPolicy, timeout));
        }
    }
}
