package com.db.vault.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON in and out. Internal: Jackson types never leave this package. */
final class Mapping {

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);

    private static final JsonNodeFactory NODES = JSON.getNodeFactory();

    private Mapping() {}

    // ------------------------------------------------------------ Java -> JSON

    static JsonNode toJson(Object value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        if (value instanceof String s) {
            return NODES.textNode(s);
        }
        if (value instanceof Boolean b) {
            return NODES.booleanNode(b);
        }
        if (value instanceof BigDecimal d) {
            return NODES.numberNode(d);
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            return NODES.numberNode(((Number) value).longValue());
        }
        if (value instanceof BigInteger i) {
            return NODES.numberNode(i);
        }
        if (value instanceof Double || value instanceof Float) {
            // Through its decimal string, so 0.1 stays 0.1.
            return NODES.numberNode(new BigDecimal(value.toString()));
        }
        if (value instanceof LocalDate || value instanceof Instant) {
            return NODES.textNode(value.toString());
        }
        if (value instanceof Enum<?> e) {
            return NODES.textNode(e.name());
        }
        if (value instanceof Ref ref) {
            ObjectNode o = NODES.objectNode();
            o.put("ref", ref.ref());
            return o;
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode o = NODES.objectNode();
            map.forEach((k, v) -> o.set(String.valueOf(k), toJson(v)));
            return o;
        }
        if (value instanceof Collection<?> list) {
            ArrayNode a = NODES.arrayNode();
            list.forEach(v -> a.add(toJson(v)));
            return a;
        }
        throw new IllegalArgumentException("Unsupported value type " + value.getClass().getName()
                + "; use String, BigDecimal, Boolean, LocalDate, Ref, Map or List");
    }

    static String write(JsonNode node) {
        try {
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    static JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return JSON.readTree(text);
        } catch (JsonProcessingException e) {
            throw new VaultClientException("The store returned invalid JSON", e);
        }
    }

    // ------------------------------------------------------------ JSON -> Java

    static Object toJava(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.properties().forEach(e -> map.put(e.getKey(), toJava(e.getValue())));
            return map;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(n -> list.add(toJava(n)));
            return list;
        }
        return node.asText();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(JsonNode node) {
        Object v = toJava(node);
        return v instanceof Map<?, ?> ? java.util.Collections.unmodifiableMap((Map<String, Object>) v) : Map.of();
    }

    static List<String> strings(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(n -> list.add(n.asText()));
        }
        return List.copyOf(list);
    }

    static Instant instant(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? null : Instant.ofEpochMilli(node.asLong());
    }

    static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    // ------------------------------------------------------------ records

    static ActorInfo actor(JsonNode n) {
        return new ActorInfo(text(n, "id"), text(n, "kind"), text(n, "displayName"), strings(n.get("roles")));
    }

    static Document document(JsonNode n) {
        Map<String, Document.Provenance> provenance = new LinkedHashMap<>();
        JsonNode prov = n.get("provenance");
        if (prov != null && prov.isObject()) {
            prov.properties().forEach(e -> {
                JsonNode p = e.getValue();
                provenance.put(e.getKey(), new Document.Provenance(p.path("eventId").asLong(), p.path("version").asInt(),
                        text(p, "actorId"), text(p, "actorKind"), text(p, "actorDisplay"), instant(p.get("at")),
                        map(p.get("evidence"))));
            });
        }
        List<Document.TransitionInfo> transitions = new ArrayList<>();
        JsonNode tr = n.get("transitions");
        if (tr != null && tr.isArray()) {
            for (JsonNode t : tr) {
                transitions.add(new Document.TransitionInfo(text(t, "name"), text(t, "label"), text(t, "to"),
                        text(t, "toLabel"), t.path("auto").asBoolean(), t.path("available").asBoolean(),
                        t.path("allowed").asBoolean(), strings(t.get("roles")), strings(t.get("requires")),
                        strings(t.get("missing")), text(t, "blockedReason")));
            }
        }
        return new Document(text(n, "id"), text(n, "type"), text(n, "typeName"), n.path("typeVersion").asInt(),
                text(n, "state"), text(n, "stateLabel"), n.path("terminal").asBoolean(), n.path("version").asInt(),
                text(n, "title"), text(n, "createdBy"), instant(n.get("createdAt")), instant(n.get("updatedAt")),
                map(n.get("content")), Map.copyOf(provenance), strings(n.get("editableFields")), List.copyOf(transitions));
    }

    static DocumentSummary summary(JsonNode n) {
        return new DocumentSummary(text(n, "id"), text(n, "type"), text(n, "typeName"), n.path("typeVersion").asInt(),
                text(n, "state"), text(n, "stateLabel"), n.path("terminal").asBoolean(), n.path("version").asInt(),
                text(n, "title"), instant(n.get("updatedAt")));
    }

    static DocumentVersion version(JsonNode n) {
        return new DocumentVersion(text(n, "documentId"), n.path("version").asInt(), text(n, "state"),
                text(n, "stateLabel"), instant(n.get("createdAt")), map(n.get("content")));
    }

    static Event event(JsonNode n) {
        return new Event(n.path("id").asLong(), text(n, "documentId"), n.path("version").asInt(), text(n, "type"),
                n.path("typeVersion").asInt(), text(n, "eventType"), actor(n.path("actor")), instant(n.get("at")),
                text(n, "proposalId"), map(n.get("payload")));
    }

    static AuditEntry audit(JsonNode n) {
        JsonNode reason = n.path("reason");
        JsonNode rv = n.get("resultVersion");
        return new AuditEntry(text(n, "id"), text(n, "kind"), text(n, "outcome"), text(n, "documentId"),
                actor(n.path("actor")), reason.isMissingNode() ? null : text(reason, "code"),
                reason.isMissingNode() ? null : text(reason, "message"),
                rv == null || rv.isNull() ? null : rv.asInt(), instant(n.get("at")));
    }

    static ProposalResult result(JsonNode n) {
        return new ProposalResult(ProposalResult.Outcome.valueOf(text(n, "outcome")), text(n, "proposalId"),
                document(n.path("document")));
    }

    static TypeInfo type(JsonNode n) {
        return new TypeInfo(text(n, "key"), text(n, "name"), n.path("version").asInt(), n.path("canCreate").asBoolean(),
                map(n.get("definition")));
    }
}
