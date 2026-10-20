package com.db.vault.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class VaultClientTest {

    static final String DOCUMENT = """
            {"id": "DEAL-1001", "type": "pipeline-deal", "typeName": "Pipeline deal", "typeVersion": 1,
             "state": "IN_CREDIT_REVIEW", "stateLabel": "In credit review", "terminal": false, "version": 3,
             "title": "Example GmbH", "createdBy": "system:crm", "createdAt": 1760000000000, "updatedAt": 1760000500000,
             "content": {"clientCompanyName": "Example GmbH", "totalRequestedVolume": 12345678901234567.89,
                         "relationshipManager": {"ref": "user:anna", "display": "Anna Schmidt"},
                         "estimatedCloseDate": "2026-12-15", "negativeInformationFlag": false,
                         "domicileAddress": {"street": "Markt 1", "city": "Oberhausen"},
                         "creditRiskApprovalComment": "[restricted]", "approvedBy": null},
             "provenance": {"clientCompanyName": {"eventId": 1, "version": 1, "actorId": "system:crm", "actorKind": "SYSTEM",
                            "actorDisplay": "CRM", "at": 1760000000000, "evidence": {"source": "crm", "reference": "OPP-1"}}},
             "editableFields": ["negativeInformationFlag"],
             "transitions": [{"name": "approve", "label": "Approve", "to": "CREDIT_APPROVED", "toLabel": "Credit approved",
                              "auto": false, "available": true, "allowed": false, "roles": ["CREDIT"],
                              "requires": ["approvedBy"], "missing": ["approvedBy"], "blockedReason": "Requires role CREDIT"}]}
            """;

    static String result(String outcome) {
        return "{\"outcome\": \"" + outcome + "\", \"proposalId\": \"p-1\", \"document\": " + DOCUMENT + "}";
    }

    static VaultClient client(FakeServer server) {
        return VaultClient.builder().baseUrl(server.baseUrl()).apiKey("secret-key")
                .retryPolicy(new RetryPolicy(3, Duration.ofMillis(10), Duration.ofMillis(50))).build();
    }

    @Test
    void createSendsKeyAndBodyAndParsesTheResult() throws Exception {
        try (FakeServer server = new FakeServer().reply(200, result("ACCEPTED"))) {
            ProposalResult r = client(server).create(NewDocument.of("pipeline-deal")
                    .set("clientCompanyName", "Example GmbH")
                    .idempotencyKey("crm-opp-1")
                    .evidence("crm", "OPP-1")
                    .build());

            FakeServer.Request sent = server.requests.get(0);
            assertEquals("POST", sent.method());
            assertEquals("/api/documents", sent.path());
            assertEquals("secret-key", sent.apiKey());
            assertTrue(sent.contentType().startsWith("application/json"));
            JsonNode body = Mapping.parse(sent.body());
            assertEquals("pipeline-deal", body.get("type").asText());
            assertEquals("crm-opp-1", body.get("idempotencyKey").asText());
            assertEquals("OPP-1", body.at("/evidence/reference").asText());

            assertEquals(ProposalResult.Outcome.ACCEPTED, r.outcome());
            assertTrue(r.changedDocument());
            assertEquals("DEAL-1001", r.document().id());
            assertEquals(3, r.document().version());
        }
    }

    @Test
    void retriesReuseTheSameIdempotencyKey() throws Exception {
        try (FakeServer server = new FakeServer()
                .reply(503, "{\"code\": \"UNAVAILABLE\", \"message\": \"try later\"}")
                .reply(502, "")
                .reply(200, result("DUPLICATE"))) {
            Proposal proposal = Proposal.builder().set("negativeInformationFlag", false).build();
            ProposalResult r = client(server).propose("DEAL-1001", proposal);

            assertEquals(3, server.requests.size());
            List<String> keys = new ArrayList<>();
            server.requests.forEach(req -> keys.add(Mapping.parse(req.body()).get("idempotencyKey").asText()));
            assertEquals(1L, keys.stream().distinct().count(), "every attempt carries the same key");
            assertEquals(proposal.idempotencyKey(), keys.get(0));
            assertEquals(ProposalResult.Outcome.DUPLICATE, r.outcome());
            assertFalse(r.changedDocument());
        }
    }

    @Test
    void rejectionsAreNotRetriedAndCarryTheirDetails() throws Exception {
        try (FakeServer server = new FakeServer().reply(409, """
                {"code": "GUARD_FAILED", "message": "'Approve' needs these fields first: approvedBy",
                 "details": {"missing": ["approvedBy"], "proposalId": "p-9"}}
                """)) {
            VaultApiException e = assertThrows(VaultApiException.class,
                    () -> client(server).propose("DEAL-1001", Proposal.builder().transition("approve").build()));
            assertEquals(1, server.requests.size(), "business rejections are final");
            assertEquals(409, e.status());
            assertTrue(e.is(ErrorCodes.GUARD_FAILED));
            assertEquals(List.of("approvedBy"), e.missingFields());
            assertEquals("p-9", e.proposalId());
        }
    }

    @Test
    void valuesAreSentExactly() throws Exception {
        try (FakeServer server = new FakeServer().reply(200, result("ACCEPTED"))) {
            client(server).propose("DEAL 1", Proposal.builder()
                    .set("totalRequestedVolume", new BigDecimal("12345678901234567.89"))
                    .set("rate", 0.1)
                    .set("relationshipManager", Ref.user("anna"))
                    .set("linkedLoan", Ref.document("LOAN-1001"))
                    .set("estimatedCloseDate", LocalDate.of(2026, 12, 15))
                    .set("domicileAddress", Map.of("city", "Oberhausen"))
                    .clear("dealName")
                    .baseVersion(3)
                    .transition("submit")
                    .build());
            FakeServer.Request sent = server.requests.get(0);
            assertEquals("/api/documents/DEAL 1/proposals", sent.path(), "ids are path-encoded");
            String raw = sent.body();
            assertTrue(raw.contains("12345678901234567.89"), raw);
            assertTrue(raw.contains("\"rate\":0.1"), raw);
            JsonNode body = Mapping.parse(raw);
            assertEquals("user:anna", body.at("/changes/relationshipManager/ref").asText());
            assertEquals("LOAN-1001", body.at("/changes/linkedLoan/ref").asText());
            assertEquals("2026-12-15", body.at("/changes/estimatedCloseDate").asText());
            assertEquals("Oberhausen", body.at("/changes/domicileAddress/city").asText());
            assertTrue(body.at("/changes/dealName").isNull(), "clear sends null");
            assertEquals(3, body.get("baseVersion").asInt());
            assertEquals("submit", body.get("transition").asText());
        }
    }

    @Test
    void documentsExposeTypedValuesProvenanceAndTransitions() throws Exception {
        try (FakeServer server = new FakeServer().reply(200, DOCUMENT)) {
            Document d = client(server).get("DEAL-1001");
            assertEquals(new BigDecimal("12345678901234567.89"), d.decimal("totalRequestedVolume"));
            assertEquals(LocalDate.of(2026, 12, 15), d.date("estimatedCloseDate"));
            assertEquals(Boolean.FALSE, d.bool("negativeInformationFlag"));
            assertEquals("Anna Schmidt", d.ref("relationshipManager").display());
            assertEquals("Oberhausen", d.object("domicileAddress").get("city"));
            assertTrue(d.isRestricted("creditRiskApprovalComment"));
            assertThrows(IllegalStateException.class, () -> d.decimal("creditRiskApprovalComment"));
            assertFalse(d.isSet("approvedBy"));
            assertEquals("OPP-1", d.provenance().get("clientCompanyName").evidence().get("reference"));
            assertFalse(d.canRequest("approve"));
            assertEquals(List.of("approvedBy"), d.transition("approve").orElseThrow().missing());
            assertTrue(d.canEdit("negativeInformationFlag"));
        }
    }

    @Test
    void findIsEmptyForUnknownDocuments() throws Exception {
        try (FakeServer server = new FakeServer().reply(404, "{\"code\": \"NOT_FOUND\", \"message\": \"Document X not found\"}")) {
            assertTrue(client(server).find("X").isEmpty());
        }
    }

    @Test
    void unreachableStoreFailsAfterRetries() throws Exception {
        String url;
        try (FakeServer server = new FakeServer()) {
            url = server.baseUrl();
        }
        VaultClient client = VaultClient.builder().baseUrl(url).apiKey("k")
                .retryPolicy(new RetryPolicy(2, Duration.ofMillis(5), Duration.ofMillis(5))).build();
        VaultClientException e = assertThrows(VaultClientException.class, () -> client.get("DEAL-1001"));
        assertFalse(e instanceof VaultApiException);
        assertTrue(e.getMessage().contains("after 2 attempt(s)"), e.getMessage());
    }

    @Test
    void subscriptionDeliversInOrderAndRetriesAFailingHandler() throws Exception {
        try (FakeServer server = new FakeServer()) {
            server.respondWith(req -> {
                long after = Long.parseLong(query(req.query(), "afterId"));
                StringBuilder events = new StringBuilder("[");
                for (long id = after + 1; id <= Math.min(after + 2, 5); id++) {
                    events.append(events.length() > 1 ? "," : "").append("""
                            {"id": %d, "documentId": "DEAL-1001", "version": %d, "type": "pipeline-deal", "typeVersion": 1,
                             "eventType": "FIELDS_UPDATED", "actor": {"id": "system:crm", "kind": "SYSTEM", "displayName": "CRM"},
                             "at": 1760000000000, "proposalId": "p", "payload": {"changes": {"dealName": {"from": null, "to": "v%d"}}}}
                            """.formatted(id, id, id));
                }
                return new FakeServer.Response(200, events.append("]").toString());
            });
            List<Long> seen = Collections.synchronizedList(new ArrayList<>());
            AtomicBoolean failedOnce = new AtomicBoolean();
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            try (EventSubscription sub = client(server).subscribe()
                    .from(0)
                    .filter(EventQuery.from(0).limit(2))
                    .pollInterval(Duration.ofMillis(20))
                    .onError(errors::add)
                    .start(event -> {
                        if (event.id() == 2 && failedOnce.compareAndSet(false, true)) {
                            throw new IllegalStateException("handler hiccup");
                        }
                        seen.add(event.id());
                    })) {
                long deadline = System.currentTimeMillis() + 3000;
                while (sub.lastEventId() < 5 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10);
                }
                assertEquals(5L, sub.lastEventId());
            }
            assertEquals(List.of(1L, 2L, 3L, 4L, 5L), seen, "in order, nothing skipped");
            assertEquals(1, errors.size());
            assertTrue(server.requests.get(0).query().contains("order=asc"), "pages oldest first");
        }
    }

    @Test
    void eventsExposeChangesAndStateChanges() {
        Event created = Mapping.event(Mapping.parse("""
                {"id": 1, "documentId": "D", "version": 1, "type": "t", "typeVersion": 1, "eventType": "DOCUMENT_CREATED",
                 "actor": {"id": "system:crm", "kind": "SYSTEM", "displayName": "CRM"}, "at": 1, "proposalId": "p",
                 "payload": {"state": "OPEN", "fields": {"a": "x"}, "evidence": {"source": "crm"}}}
                """));
        assertEquals("x", created.changes().get("a").to());
        assertEquals("crm", created.evidence().get("source"));
        assertTrue(created.actor().isSystem());
        Event moved = Mapping.event(Mapping.parse("""
                {"id": 2, "documentId": "D", "version": 2, "type": "t", "typeVersion": 1, "eventType": "STATE_CHANGED",
                 "actor": {"id": "system:document-store", "kind": "SYSTEM", "displayName": "Document store"}, "at": 2,
                 "proposalId": "p", "payload": {"transition": "ready", "from": "OPEN", "to": "READY", "derived": true}}
                """));
        Event.StateChange change = moved.stateChange().orElseThrow();
        assertEquals("READY", change.to());
        assertTrue(change.derived());
    }

    @Test
    void emptyProposalsAreRefusedBeforeSending() {
        assertThrows(IllegalStateException.class, () -> Proposal.builder().build());
    }

    private static String query(String raw, String key) {
        for (String part : raw.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }
}
