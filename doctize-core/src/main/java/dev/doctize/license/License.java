package dev.doctize.license;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.doctize.annotations.Doc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a license grants: to whom, until when, and which {@link Feature}s.
 *
 * <p>A license is issued as a signed file, see {@link LicenseVerifier}. This record is the signed content.
 *
 * @param id the identifier of the license, for support requests
 * @param licensee who the license was issued to
 * @param issued the day it was issued
 * @param expires the last day it is valid; {@code null} for a license that does not expire
 * @param features what it unlocks
 */
@Doc(group = "Licensing", order = 61)
public record License(String id, String licensee, LocalDate issued, LocalDate expires, Set<Feature> features) {

    static final int FORMAT_VERSION = 1;
    private static final ObjectMapper JSON = new ObjectMapper();

    public License {
        if (id == null || id.isBlank() || licensee == null || licensee.isBlank() || issued == null) {
            throw new IllegalArgumentException("A license needs an id, a licensee and an issue date");
        }
        features = features == null || features.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(features));
    }

    public boolean allows(Feature feature) {
        return features.contains(feature);
    }

    /** Whether the license has run out on the given day. It is still valid on its last day. */
    public boolean expiredOn(LocalDate day) {
        return expires != null && day.isAfter(expires);
    }

    /** The bytes that are signed when the license is issued. */
    public byte[] toJson() {
        ObjectNode node = JSON.createObjectNode();
        node.put("version", FORMAT_VERSION);
        node.put("id", id);
        node.put("licensee", licensee);
        node.put("issued", issued.toString());
        if (expires != null) {
            node.put("expires", expires.toString());
        }
        ArrayNode list = node.putArray("features");
        features.stream().map(Feature::id).sorted().forEach(list::add);
        try {
            return JSON.writeValueAsBytes(node);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Reads what {@link #toJson()} wrote. Features this version of Doctize does not know are ignored. */
    static License fromJson(byte[] json) {
        try {
            JsonNode node = JSON.readTree(new String(json, StandardCharsets.UTF_8));
            int version = node.path("version").asInt(-1);
            if (version != FORMAT_VERSION) {
                throw new LicenseException("This license has format version " + version
                        + ", which this version of Doctize does not understand. Update Doctize.");
            }
            Set<Feature> features = EnumSet.noneOf(Feature.class);
            node.path("features").forEach(feature -> Feature.byId(feature.asText()).ifPresent(features::add));
            return new License(
                    text(node, "id"),
                    text(node, "licensee"),
                    LocalDate.parse(text(node, "issued")),
                    node.hasNonNull("expires") ? LocalDate.parse(text(node, "expires")) : null,
                    features);
        } catch (IOException | DateTimeException | IllegalArgumentException e) {
            throw new LicenseException("The content of the license is damaged: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            throw new IllegalArgumentException("'" + field + "' is missing");
        }
        return node.get(field).asText();
    }

    /** A short description for messages, e.g. {@code Acme Corp, valid until 2027-09-20: architecture}. */
    public String summary() {
        String validity = expires == null ? "does not expire" : "valid until " + expires;
        String list = features.stream().map(Feature::id).sorted().collect(Collectors.joining(", "));
        return licensee + ", " + validity + (list.isEmpty() ? "" : ": " + list);
    }
}
