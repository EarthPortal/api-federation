package org.semantics.apigateway.artefacts.search;

import org.semantics.apigateway.model.responses.AggregatedApiResponse;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NercSparqlEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(NercSparqlEnrichmentService.class);

    private static final String NERC_BACKEND_TYPE = "nerc";
    private static final String SPARQL_PATH = "/sparql/sparql";

    private static final String SPARQL_QUERY_TEMPLATE =
            "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>\n" +
            "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "SELECT ?iri ?altLabel ?definition ?broader ?narrower ?deprecated\n" +
            "WHERE {\n" +
            "  VALUES ?iri { %s }\n" +
            "  OPTIONAL { ?iri skos:altLabel ?altLabel }\n" +
            "  OPTIONAL { ?iri skos:definition ?definition }\n" +
            "  OPTIONAL { ?iri skos:broader ?broader }\n" +
            "  OPTIONAL { ?iri skos:narrower ?narrower }\n" +
            "  OPTIONAL { ?iri owl:deprecated ?deprecated }\n" +
            "}";

    private final WebClient webClient;
    private final ConfigurationLoader configurationLoader;

    public NercSparqlEnrichmentService(WebClient.Builder webClientBuilder, ConfigurationLoader configurationLoader) {
        this.webClient = webClientBuilder.build();
        this.configurationLoader = configurationLoader;
    }

    public AggregatedApiResponse enrich(AggregatedApiResponse data) {
        if (data == null || data.getCollection() == null || data.getCollection().isEmpty()) {
            return data;
        }

        List<Map<String, Object>> nercItems = data.getCollection().stream()
                .filter(this::isNercItem)
                .collect(Collectors.toList());

        if (nercItems.isEmpty()) {
            return data;
        }

        // Normalize @type for every NERC item, regardless of SPARQL outcome
        nercItems.forEach(this::normalizeType);

        List<String> iris = nercItems.stream()
                .map(item -> (String) item.get("iri"))
                .filter(Objects::nonNull)
                .map(this::normalizeIri)
                .distinct()
                .collect(Collectors.toList());

        if (iris.isEmpty()) {
            return data;
        }

        Map<String, Enrichment> enrichmentMap;
        try {
            enrichmentMap = fetchAndGroup(iris);
        } catch (Exception e) {
            logger.warn("NERC SPARQL enrichment failed, returning data unenriched: {}", e.getMessage());
            return data;
        }

        logger.info("NERC SPARQL enrichment: {} items, {} enrichment groups", nercItems.size(), enrichmentMap.size());

        for (Map<String, Object> item : nercItems) {
            String iri = normalizeIri((String) item.get("iri"));
            Enrichment e = enrichmentMap.get(iri);
            if (e != null) {
                mergeIntoItem(item, e);
            }
        }

        return data;
    }

    private String normalizeIri(String iri) {
        if (iri == null) return null;
        return iri.endsWith("/") ? iri : iri + "/";
    }

    private boolean isNercItem(Map<String, Object> item) {
        Object backendType = item.get("backend_type");
        return backendType != null && NERC_BACKEND_TYPE.equalsIgnoreCase(backendType.toString());
    }

    private Map<String, Enrichment> fetchAndGroup(List<String> iris) {
        String sparqlEndpoint = resolveSparqlEndpoint();
        String values = iris.stream()
                .map(iri -> "<" + iri + ">")
                .collect(Collectors.joining(" "));
        String query = String.format(SPARQL_QUERY_TEMPLATE, values);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", query);

        Map<String, Object> response = webClient.post()
                .uri(sparqlEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.valueOf("application/sparql-results+json"))
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(15))
                .block();

        return parseAndGroup(response);
    }

    private String resolveSparqlEndpoint() {
        String baseUrl = configurationLoader.getConfigByName(NERC_BACKEND_TYPE).getUrl();
        if (baseUrl == null) {
            throw new IllegalStateException("NERC base URL not configured in databases.json");
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + SPARQL_PATH;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Enrichment> parseAndGroup(Map<String, Object> response) {
        Map<String, Enrichment> grouped = new HashMap<>();
        if (response == null) {
            return grouped;
        }
        Map<String, Object> results = (Map<String, Object>) response.get("results");
        if (results == null) {
            return grouped;
        }
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) results.get("bindings");
        if (bindings == null) {
            return grouped;
        }

        for (Map<String, Object> binding : bindings) {
            String iri = readValue(binding, "iri");
            if (iri == null) {
                continue;
            }

            Enrichment e = grouped.computeIfAbsent(iri, k -> new Enrichment());

            String altLabel = readValue(binding, "altLabel");
            if (altLabel != null && !altLabel.isEmpty()) {
                e.synonyms.add(altLabel);
            }

            String definition = readValue(binding, "definition");
            if (definition != null && !definition.isEmpty()) {
                e.descriptions.add(definition);
                String lang = readLang(binding, "definition");
                if (lang != null && e.lang == null) {
                    e.lang = lang;
                }
            }

            String broader = readValue(binding, "broader");
            if (broader != null) {
                e.broader.add(broader);
            }

            String narrower = readValue(binding, "narrower");
            if (narrower != null) {
                e.narrower.add(narrower);
            }

            String deprecated = readValue(binding, "deprecated");
            if (deprecated != null) {
                e.obsolete = Boolean.parseBoolean(deprecated);
            }
        }

        return grouped;
    }

    @SuppressWarnings("unchecked")
    private String readValue(Map<String, Object> binding, String key) {
        Object node = binding.get(key);
        if (!(node instanceof Map)) {
            return null;
        }
        Object value = ((Map<String, Object>) node).get("value");
        return value == null ? null : value.toString();
    }

    @SuppressWarnings("unchecked")
    private String readLang(Map<String, Object> binding, String key) {
        Object node = binding.get(key);
        if (!(node instanceof Map)) {
            return null;
        }
        Object lang = ((Map<String, Object>) node).get("xml:lang");
        return lang == null ? null : lang.toString();
    }

    private void mergeIntoItem(Map<String, Object> item, Enrichment e) {
        if (!e.synonyms.isEmpty()) {
            item.put("synonyms", new ArrayList<>(e.synonyms));
        }
        if (!e.descriptions.isEmpty()) {
            item.put("descriptions", new ArrayList<>(e.descriptions));
        }
        item.put("obsolete", e.obsolete);
        item.put("broader", new ArrayList<>(e.broader));
        item.put("narrower", new ArrayList<>(e.narrower));
        if (e.lang != null) {
            item.putIfAbsent("lang", e.lang);
        }
    }

    private void normalizeType(Map<String, Object> item) {
        item.put("type", "http://www.w3.org/2004/02/skos/core#Concept");
    }

    private static class Enrichment {
        final Set<String> synonyms = new LinkedHashSet<>();
        final Set<String> descriptions = new LinkedHashSet<>();
        final Set<String> broader = new LinkedHashSet<>();
        final Set<String> narrower = new LinkedHashSet<>();
        boolean obsolete = false;
        String lang;
    }
}
