package org.semantics.apigateway.artefacts.recommender;

import org.semantics.apigateway.artefacts.metadata.ArtefactsService;
import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.RDFResource;
import org.semantics.apigateway.model.responses.AggregatedApiResponse;
import org.semantics.apigateway.model.responses.ApiResponse;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.AbstractEndpointService;
import org.semantics.apigateway.service.ApiAccessor;
import org.semantics.apigateway.service.JsonLdTransform;
import org.semantics.apigateway.service.ResponseTransformerService;
import org.semantics.apigateway.service.UrlConfig;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.semantics.apigateway.util.OntoPortalUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Service
public class RecommenderService extends AbstractEndpointService {

    private static final Logger logger = LoggerFactory.getLogger(RecommenderService.class);
    private final OntoPortalUtil ontoPortalUtil;
    private final ArtefactsService artefactsService;

    public RecommenderService(ConfigurationLoader configurationLoader, CacheManager cacheManager, JsonLdTransform jsonLdTransform, ResponseTransformerService responseTransformerService, OntoPortalUtil ontoPortalUtil, ArtefactsService artefactsService) {
        super(configurationLoader, cacheManager, jsonLdTransform, responseTransformerService, RDFResource.class);
        this.ontoPortalUtil = ontoPortalUtil;
        this.artefactsService = artefactsService;
    }

    public Object performRecommendation(String database, String input, String inputType, String outputType, String ontologies, String maxElementsSet, String coverageWeight, String acceptanceWeight, String detailWeight, String specializationWeight, User currentUser) {
        input = sanitize(input);

        OntologyIndex index = buildOntologyIndex(database, currentUser);

        ApiAccessor accessor = initAccessor(database, "recommender", null);
        Set<String> scope = scopePortals(database);
        accessor = restrictToScope(accessor, scope);
        accessor = applyParam(accessor, "input_type", inputTypeCode(inputType));
        String outputTypeCode = outputTypeCode(outputType);
        accessor = applyParam(accessor, "output_type", outputTypeCode);
        if (outputTypeCode.equals("2")) {
            accessor = applyParam(accessor, "max_elements_set", maxElementsSet);
        }
        accessor = applyParam(accessor, "wc", coverageWeight);
        accessor = applyParam(accessor, "wa", acceptanceWeight);
        accessor = applyParam(accessor, "wd", detailWeight);
        accessor = applyParam(accessor, "ws", specializationWeight);
        if (ontologies != null && !ontologies.isBlank()) {
            accessor = applyParam(accessor, "ontologies", ontologies.trim());
        }
        try {
            Map<String, ApiResponse> responses = accessor.get(input).get();
            List<Object> all = new ArrayList<>();
            for (ApiResponse response : responses.values()) {
                if (response == null || response.getResponseBody() == null) continue;
                Object collection = response.getResponseBody().get("collection");
                if (collection instanceof List) {
                    all.addAll((List<?>) collection);
                }
            }
            List<Object> merged = mergeByCanonical(all, index, scope);
            sortByScore(merged);
            return buildResponse(merged, scope);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private OntologyIndex buildOntologyIndex(String database, User currentUser) {
        OntologyIndex index = new OntologyIndex();
        AggregatedApiResponse artefacts = artefactsService.getDeduplicatedArtefacts(database, new CommonRequestParams(), currentUser);
        if (artefacts == null || artefacts.getCollection() == null) return index;

        for (Map<String, Object> item : artefacts.getCollection()) {
            String acronym = acronymOf(item);
            Object canonical = item.get("source_name");
            if (acronym == null || canonical == null) continue;
            index.canonicalByAcronym.put(acronym.toUpperCase(), canonical.toString());
        }
        logger.info("Recommender ontology index built: {} ontologies", index.canonicalByAcronym.size());
        return index;
    }

    private String acronymOf(Map<String, Object> item) {
        Object shortForm = item.get("short_form");
        if (shortForm != null && !shortForm.toString().isEmpty()) return shortForm.toString();
        Object sourceUrl = item.get("source_url");
        if (sourceUrl == null) sourceUrl = item.get("sourceUrl");
        if (sourceUrl == null) return null;
        String url = sourceUrl.toString();
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    @SuppressWarnings("unchecked")
    private List<Object> mergeByCanonical(List<Object> all, OntologyIndex index, Set<String> portals) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Object obj : all) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> item = (Map<String, Object>) obj;
            String acronym = recommendationAcronym(item);
            if (acronym == null) continue;
            groups.computeIfAbsent(acronym.toUpperCase(), k -> new ArrayList<>()).add(item);
        }

        List<Object> merged = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            List<Map<String, Object>> group = entry.getValue();
            Map<String, Object> canonical = new LinkedHashMap<>(pickCanonicalCopy(group, index.canonicalByAcronym.get(entry.getKey()), portals));
            List<Object> sources = sourcesOf(group, portals);
            if (sources.size() > 1) {
                canonical.put("found_in", sources);
            } else {
                canonical.remove("found_in");
            }
            merged.add(canonical);
        }
        return merged;
    }

    private Map<String, Object> pickCanonicalCopy(List<Map<String, Object>> group, String canonicalPortal, Set<String> portals) {
        if (canonicalPortal != null) {
            for (Map<String, Object> item : group) {
                if (canonicalPortal.equalsIgnoreCase(portalNameOf(ontologyUrl(item), portals))) return item;
            }
        }
        Map<String, Object> best = group.get(0);
        for (Map<String, Object> item : group) {
            if (scoreOf(item) > scoreOf(best)) best = item;
        }
        return best;
    }

    private List<Object> sourcesOf(List<Map<String, Object>> group, Set<String> portals) {
        List<Object> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> item : group) {
            String portal = portalNameOf(ontologyUrl(item), portals);
            if (portal == null || !seen.add(portal)) continue;
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("portal", portal);
            link.put("ui_link", ontologyUiLink(item));
            list.add(link);
        }
        return list;
    }

    private void sortByScore(List<Object> recommendations) {
        recommendations.sort((a, b) -> Double.compare(scoreOf(b), scoreOf(a)));
    }

    @SuppressWarnings("unchecked")
    private double scoreOf(Object obj) {
        if (!(obj instanceof Map)) return 0;
        Object score = ((Map<String, Object>) obj).get("evaluationScore");
        if (score == null) return 0;
        try {
            return Double.parseDouble(score.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Map<String, Object> buildResponse(List<Object> collection, Set<String> portals) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCount", collection.size());
        response.put("countByPortal", countByPortal(collection, portals));
        response.put("collection", collection);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> countByPortal(List<Object> collection, Set<String> portals) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Object obj : collection) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> item = (Map<String, Object>) obj;
            Object foundIn = item.get("found_in");
            if (foundIn instanceof List && !((List<?>) foundIn).isEmpty()) {
                for (Object entry : (List<?>) foundIn) {
                    if (!(entry instanceof Map)) continue;
                    Object portal = ((Map<String, Object>) entry).get("portal");
                    if (portal != null) counts.merge(portal.toString().toLowerCase(), 1, Integer::sum);
                }
            } else {
                String portal = portalNameOf(ontologyUrl(item), portals);
                if (portal != null) counts.merge(portal, 1, Integer::sum);
            }
        }
        return counts;
    }

    private String recommendationAcronym(Map<String, Object> item) {
        Map<String, Object> ontology = firstOntology(item);
        if (ontology == null) return null;
        Object acronym = ontology.get("acronym");
        if (acronym != null && !acronym.toString().isEmpty()) return acronym.toString();
        String url = ontologyUrl(item);
        if (url == null) return null;
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    @SuppressWarnings("unchecked")
    private String ontologyUrl(Map<String, Object> item) {
        Map<String, Object> ontology = firstOntology(item);
        if (ontology == null) return null;
        Object id = ontology.get("@id");
        if (id == null) {
            Map<String, Object> links = (Map<String, Object>) ontology.get("links");
            if (links != null) id = links.get("ui");
        }
        return id == null ? null : id.toString();
    }

    @SuppressWarnings("unchecked")
    private String ontologyUiLink(Map<String, Object> item) {
        Map<String, Object> ontology = firstOntology(item);
        if (ontology == null) return null;
        Map<String, Object> links = (Map<String, Object>) ontology.get("links");
        if (links == null || links.get("ui") == null) return null;
        return links.get("ui").toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstOntology(Map<String, Object> item) {
        Object ontologies = item.get("ontologies");
        if (!(ontologies instanceof List)) return null;
        List<?> list = (List<?>) ontologies;
        if (list.isEmpty()) return null;
        Object first = list.get(0);
        return first instanceof Map ? (Map<String, Object>) first : null;
    }

    private Set<String> scopePortals(String database) {
        Set<String> all = ontoPortalUtil.getOntoPortalPortals();
        if (database == null || database.isBlank()) return all;
        Set<String> scope = new LinkedHashSet<>();
        for (String token : database.split(",")) {
            String t = token.trim().toLowerCase();
            if (t.isEmpty()) continue;
            for (String portal : all) {
                if (t.equals(portal)) scope.add(portal);
            }
        }
        return scope.isEmpty() ? all : scope;
    }

    private ApiAccessor restrictToScope(ApiAccessor accessor, Set<String> scope) {
        Map<String, UrlConfig> kept = new LinkedHashMap<>();
        accessor.getUrls().forEach((url, cfg) -> {
            if (portalNameOf(url, scope) != null) kept.put(url, cfg);
        });
        if (!kept.isEmpty()) accessor.setUrls(kept);
        return accessor;
    }

    private String portalNameOf(String url, Set<String> portals) {
        if (url == null) return null;
        String lower = url.toLowerCase();
        for (String portal : portals) {
            if (lower.contains(portal)) return portal;
        }
        return null;
    }

    private String inputTypeCode(String value) {
        if (value == null) return "1";
        String v = value.trim().toLowerCase();
        return (v.equals("keywords") || v.equals("2")) ? "2" : "1";
    }

    private String outputTypeCode(String value) {
        if (value == null) return "1";
        String v = value.trim().toLowerCase();
        return (v.equals("sets") || v.equals("2")) ? "2" : "1";
    }

    private String sanitize(String text) {
        if (text == null) return null;
        return text.replaceAll("[%&#]", " ");
    }

    private ApiAccessor applyParam(ApiAccessor accessor, String name, String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("none")) {
            return accessor;
        }
        Map<String, UrlConfig> updated = new LinkedHashMap<>();
        accessor.getUrls().forEach((url, cfg) -> {
            String sep = url.contains("?") ? "&" : "?";
            updated.put(url + sep + name + "=" + value, cfg);
        });
        accessor.setUrls(updated);
        return accessor;
    }

    private static class OntologyIndex {
        final Map<String, String> canonicalByAcronym = new HashMap<>();
    }
}
