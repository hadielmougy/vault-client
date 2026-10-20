# Document store Java client

A small client for services that create and change documents in the store, or
follow its event store.

- Java 17+, built on the JDK `HttpClient`. Jackson 2 is used internally only:
  no Jackson type appears in the API, so it works next to Spring Boot 3
  (Jackson 2) and Spring Boot 4 (Jackson 3).
- Values are plain Java: `String`, `BigDecimal` (never double), `Boolean`,
  `LocalDate`, `Ref` for references, `Map` for embedded objects.
- Safe retries: every `NewDocument` and `Proposal` gets an idempotency key when
  built and keeps it across retries, so a retried write is never applied twice.
- Rejections are typed: `VaultApiException` with a stable `code()`.

## Add it

```kotlin
dependencies {
    implementation("com.example.vault:document-store-client:0.1.0")
}
```

Publish it to your repository with
`./gradlew :client:publish -PinternalRepoUrl=... -PinternalUsername=... -PinternalPassword=...`.

## Use it

```java
VaultClient store = VaultClient.builder()
        .baseUrl("https://vault.example")
        .apiKey(System.getenv("VAULT_API_KEY"))
        .build();                                   // thread-safe, create one and share it

// Create: an external event becomes a document.
Document deal = store.create(NewDocument.of("pipeline-deal")
        .set("clientCompanyName", "Example GmbH")
        .set("domicileAddress", Map.of("street", "Markt 1", "postalCode", "46145", "city", "Oberhausen", "country", "DE"))
        .set("relationshipManager", Ref.user("anna"))
        .idempotencyKey("crm-opp-4711")              // your event id: redelivery is deduplicated too
        .evidence("crm", "CRM-OPP-4711")
        .build()).document();

// Propose: fields and a transition, applied atomically.
try {
    store.propose(deal.id(), Proposal.builder()
            .set("countryOfRisk", "DE")
            .transition("submit")
            .baseVersion(deal.version())             // optional optimistic concurrency
            .build());
} catch (VaultApiException e) {
    if (e.is(ErrorCodes.GUARD_FAILED)) {
        log.info("Submit still needs {}", e.missingFields());
    } else if (e.is(ErrorCodes.STALE_VERSION)) {
        log.info("Someone else changed it; now at version {}", e.currentVersion());
    } else {
        throw e;
    }
}

// Read: typed values, redaction, provenance, available transitions.
Document current = store.get(deal.id());
BigDecimal volume = current.decimal("totalRequestedVolume");
boolean hidden = current.isRestricted("creditRiskApprovalComment");
boolean canSubmit = current.canRequest("submit");
```

## Follow the event store

Until events are published to Kafka, consumers poll. The subscription does it
for you: in order, at least once, resumable.

```java
try (EventSubscription sub = store.subscribe()
        .from(checkpoint.load())                     // last processed event id, 0 for everything
        .filter(EventQuery.from(0).ofEventType(Event.STATE_CHANGED))
        .onError(e -> log.warn("Event handling failed, retrying", e))
        .start(event -> {
            event.stateChange().ifPresent(change -> react(event.documentId(), change.to()));
            checkpoint.save(event.id());
        })) {
    ...
}
```

A handler that throws gets the same event again after a pause; events are never
skipped. Make handlers idempotent (the event id is unique).

## Errors

| Code | Status | Meaning |
|---|---|---|
| `NOT_FIELD_OWNER` | 403 | Your roles do not own a field you changed |
| `TRANSITION_NOT_PERMITTED` | 403 | Your roles may not request the transition |
| `GUARD_FAILED` | 409 | Required fields missing: `missingFields()` |
| `STALE_VERSION` | 409 | `baseVersion` is behind: `currentVersion()` |
| `DOCUMENT_CLOSED` | 409 | The document is in a final state |
| `INVALID_VALUE` | 400 | A value does not fit its field: `fieldProblems()` |

Network failures (after retries) are `VaultClientException`. Retries cover
connection errors and 429, 500, 502, 503, 504, honouring `Retry-After`;
configure with `RetryPolicy`.

## Not yet

- Bearer tokens: `Credentials.bearer(...)` is there, the store accepts API keys only so far.
- A Spring Boot starter (auto-configured bean from properties).
