package org.semantics.apigateway.artefacts.annotator;

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
public class AnnotatorService extends AbstractEndpointService {

    private static final Logger logger = LoggerFactory.getLogger(AnnotatorService.class);
    private final OntoPortalUtil ontoPortalUtil;
    private final ArtefactsService artefactsService;

    public AnnotatorService(ConfigurationLoader configurationLoader, CacheManager cacheManager, JsonLdTransform jsonLdTransform, ResponseTransformerService responseTransformerService, OntoPortalUtil ontoPortalUtil, ArtefactsService artefactsService) {
        super(configurationLoader, cacheManager, jsonLdTransform, responseTransformerService, RDFResource.class);
        this.ontoPortalUtil = ontoPortalUtil;
        this.artefactsService = artefactsService;
    }

    public Object performAnnotation(String database, String text, String ontologies, boolean expandMappings, boolean excludeNumbers, boolean excludeSynonyms, boolean wholeWordOnly, boolean longestOnly, String classHierarchyMaxLevel, User currentUser) {
        text = removeSpecialChars(text);

        OntologyIndex index = buildOntologyIndex(database, currentUser);

        ApiAccessor accessor = initAccessor(database, "annotator", null);
        accessor = routeToCanonical(accessor, ontologies, index);
        accessor = applyFlag(accessor, "expand_mappings", expandMappings);
        accessor = applyFlag(accessor, "exclude_numbers", excludeNumbers);
        accessor = applyFlag(accessor, "exclude_synonyms", excludeSynonyms);
        accessor = applyFlag(accessor, "whole_word_only", wholeWordOnly);
        accessor = applyFlag(accessor, "longest_only", longestOnly);
        accessor = applyParam(accessor, "class_hierarchy_max_level", classHierarchyMaxLevel);
        try {
            Map<String, ApiResponse> responses = accessor.get(text).get();
            List<Object> annotations = new ArrayList<>();
            for (ApiResponse response : responses.values()) {
                if (response == null || response.getResponseBody() == null) continue;
                Object collection = response.getResponseBody().get("collection");
                if (collection instanceof List) {
                    annotations.addAll((List<?>) collection);
                }
            }
            attachFoundIn(annotations, index);
            return buildResponse(annotations);
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
            String key = acronym.toUpperCase();
            index.canonicalByAcronym.put(key, canonical.toString());
            Object foundIn = item.get("found_in");
            if (foundIn != null) index.foundInByAcronym.put(key, foundIn);
        }
        logger.info("Annotator ontology index built: {} ontologies", index.canonicalByAcronym.size());
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

    private ApiAccessor routeToCanonical(ApiAccessor accessor, String ontologies, OntologyIndex index) {
        Set<String> requested = new LinkedHashSet<>();
        if (ontologies == null || ontologies.isBlank()) {
            requested.addAll(index.canonicalByAcronym.keySet());
        } else {
            for (String a : ontologies.split(",")) {
                String t = a.trim().toUpperCase();
                if (!t.isEmpty()) requested.add(t);
            }
        }

        Set<String> portals = ontoPortalUtil.getOntoPortalPortals();
        Map<String, UrlConfig> updated = new LinkedHashMap<>();
        accessor.getUrls().forEach((url, cfg) -> {
            String portalName = portalNameOf(url, portals);
            if (portalName == null) {
                updated.put(url, cfg);
                return;
            }
            List<String> forPortal = new ArrayList<>();
            for (String acronym : requested) {
                String canon = index.canonicalByAcronym.get(acronym);
                if (canon != null && canon.equalsIgnoreCase(portalName)) forPortal.add(acronym);
            }
            if (forPortal.isEmpty()) return;
            String sep = url.contains("?") ? "&" : "?";
            updated.put(url + sep + "ontologies=" + String.join(",", forPortal), cfg);
        });
        accessor.setUrls(updated);
        return accessor;
    }

    @SuppressWarnings("unchecked")
    private void attachFoundIn(List<Object> annotations, OntologyIndex index) {
        for (Object obj : annotations) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> item = (Map<String, Object>) obj;
            String acronym = ontologyAcronym(item);
            if (acronym == null) continue;
            Object foundIn = index.foundInByAcronym.get(acronym.toUpperCase());
            if (foundIn instanceof List) item.put("found_in", toPortalLinks((List<?>) foundIn));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> toPortalLinks(List<?> foundIn) {
        List<Object> result = new ArrayList<>();
        for (Object obj : foundIn) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> entry = (Map<String, Object>) obj;
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("portal", entry.get("source_name"));
            link.put("ui_link", entry.get("source_url"));
            result.add(link);
        }
        return result;
    }

    private Map<String, Object> buildResponse(List<Object> collection) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalCount", collection.size());
        response.put("countByPortal", countByPortal(collection));
        response.put("collection", collection);
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> countByPortal(List<Object> collection) {
        Set<String> portals = ontoPortalUtil.getOntoPortalPortals();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Object obj : collection) {
            if (!(obj instanceof Map)) continue;
            Map<String, Object> item = (Map<String, Object>) obj;
            Set<String> itemPortals = new LinkedHashSet<>();
            addPortal(itemPortals, ontologyUrl(item), portals);
            Object foundIn = item.get("found_in");
            if (foundIn instanceof List) {
                for (Object entry : (List<?>) foundIn) {
                    if (entry instanceof Map) {
                        Object portal = ((Map<String, Object>) entry).get("portal");
                        if (portal != null && portals.contains(portal.toString().toLowerCase())) {
                            itemPortals.add(portal.toString().toLowerCase());
                        }
                    }
                }
            }
            for (String portal : itemPortals) counts.merge(portal, 1, Integer::sum);
        }
        return counts;
    }

    private void addPortal(Set<String> accumulator, String url, Set<String> portals) {
        if (url == null) return;
        String lower = url.toLowerCase();
        for (String portal : portals) {
            if (lower.contains(portal)) {
                accumulator.add(portal);
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String ontologyAcronym(Map<String, Object> item) {
        String url = ontologyUrl(item);
        if (url == null) return null;
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    @SuppressWarnings("unchecked")
    private String ontologyUrl(Map<String, Object> item) {
        Map<String, Object> annotatedClass = (Map<String, Object>) item.get("annotatedClass");
        if (annotatedClass == null) return null;
        Map<String, Object> links = (Map<String, Object>) annotatedClass.get("links");
        return links == null || links.get("ontology") == null ? null : links.get("ontology").toString();
    }

    private String portalNameOf(String url, Set<String> portals) {
        if (url == null) return null;
        String lower = url.toLowerCase();
        for (String portal : portals) {
            if (lower.contains(portal)) return portal;
        }
        return null;
    }

    private String removeSpecialChars(String text) {
        if (text == null) return null;
        if (text.matches("^[a-zA-Z0-9\\s]*$")) return text;
        return text.replaceAll("[^\\w\\s]", "");
    }

    private ApiAccessor applyFlag(ApiAccessor accessor, String name, boolean value) {
        if (!value) {
            return accessor;
        }
        Map<String, UrlConfig> updated = new LinkedHashMap<>();
        accessor.getUrls().forEach((url, cfg) -> {
            String sep = url.contains("?") ? "&" : "?";
            updated.put(url + sep + name + "=true", cfg);
        });
        accessor.setUrls(updated);
        return accessor;
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
        final Map<String, Object> foundInByAcronym = new HashMap<>();
    }
}
