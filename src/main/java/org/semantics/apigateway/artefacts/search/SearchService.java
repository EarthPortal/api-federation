package org.semantics.apigateway.artefacts.search;

import org.apache.lucene.queryparser.classic.ParseException;
import org.semantics.apigateway.collections.CollectionService;
import org.semantics.apigateway.collections.models.TerminologyCollection;
import org.semantics.apigateway.config.DatabaseConfig;
import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.RDFResource;
import org.semantics.apigateway.model.TargetDbSchema;
import org.semantics.apigateway.model.responses.AggregatedApiResponse;
import org.semantics.apigateway.model.responses.ApiResponse;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.AbstractEndpointService;
import org.semantics.apigateway.service.ApiAccessor;
import org.semantics.apigateway.service.JsonLdTransform;
import org.semantics.apigateway.service.ResponseTransformerService;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class SearchService extends AbstractEndpointService {


    private final SearchLocalIndexerService localIndexer;
    private final SearchDeduplicationService deduplicationService;
    private final OntologyCategoryCache categoryCache;

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);
    private final CollectionService collectionService;

    public SearchService(ConfigurationLoader configurationLoader, SearchLocalIndexerService localIndexer, CacheManager cacheManager, JsonLdTransform jsonLdTransform, ResponseTransformerService responseTransformerService, CollectionService collectionService, SearchDeduplicationService deduplicationService, OntologyCategoryCache categoryCache) {
        super(configurationLoader, cacheManager, jsonLdTransform, responseTransformerService, RDFResource.class);
        this.localIndexer = localIndexer;
        this.collectionService = collectionService;
        this.deduplicationService = deduplicationService;
        this.categoryCache = categoryCache;
    }

    public AggregatedApiResponse performSearch(String query, String database, String targetDbSchema, boolean showResponseConfiguration) {
        TargetDbSchema targetDbSchemaEnum = targetDbSchema == null ? null : TargetDbSchema.valueOf(targetDbSchema);
        CommonRequestParams commonRequestParams = new CommonRequestParams();
        commonRequestParams.setTargetDbSchema(targetDbSchemaEnum);
        commonRequestParams.setShowResponseConfiguration(showResponseConfiguration);
        return performSearch(database, query, commonRequestParams, null, null, null);
    }

    public AggregatedApiResponse performSearch(
            String database,
            String query,
            CommonRequestParams params,
            String collectionId,
            User currentUser,
            ApiAccessor accessor) {
        String endpoint = "search";
        TargetDbSchema targetDbSchema = params.getTargetDbSchema();
        TerminologyCollection collection = collectionService.getCurrentUserCollection(collectionId, currentUser);
        accessor = initAccessor(database, endpoint, accessor);
        accessor = applyCollection(accessor, collection, endpoint);
        accessor = applyLang(accessor, params.getLang());
        
        try {
            return accessor.get(query)
                    .thenApply(raw -> normalizeOntoPortalMultilingualRaw(raw, query, params.getLang()))
                    .thenApply(data -> this.transformApiResponses(data, endpoint))
                    .thenApply(transformedData -> flattenResponseList(transformedData, params, collection))
                    .thenApply(data -> normalizeMultilingualLabels(data, query, params.getLang()))
                    .thenApply(data -> filterOutByCollection(collection, data))
                    .thenApply(this::enrichWithCategories)
                    .thenApply(this::deduplicateResults)
                    .thenApply(data -> filterByCategories(data, params.getCategories()))
                    .thenApply(data -> reIndexResults(query, data))
                    .thenApply(x -> transformJsonLd(x, params))
                    .thenApply(data -> transformForTargetDbSchema(data, targetDbSchema, endpoint, params.getLang()))
                    .get();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private Map<String, ApiResponse> normalizeOntoPortalMultilingualRaw(
            Map<String, ApiResponse> data, String query, String langParam) {
        if (!isMultiLang(langParam)) {
            return data;
        }
        List<String> requestedLangs = parseLangs(langParam);

        data.forEach((url, response) -> {
            if (response == null || response.getResponseBody() == null) return;

            DatabaseConfig config;
            try {
                URL u = new URL(url);
                String baseUrl = u.getProtocol() + "://" + u.getHost();
                config = configurationLoader.getConfigByBaseUrl(baseUrl);
            } catch (Exception e) {
                return;
            }
            if (config == null || !config.isOntoPortal()) return;

            Object collection = response.getResponseBody().get("collection");
            if (!(collection instanceof List)) return;

            for (Object itemObj : (List<?>) collection) {
                if (!(itemObj instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) itemObj;

                Object prefLabel = item.get("prefLabel");
                if (prefLabel instanceof Map<?, ?> map && !map.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> byLang = (Map<String, Object>) map;
                    item.put("prefLabelMap", new LinkedHashMap<>(byLang));
                    String picked = pickLabel(byLang, query, requestedLangs);
                    if (picked != null) {
                        item.put("prefLabel", picked);
                    }
                }
            }
        });
        return data;
    }

    private AggregatedApiResponse normalizeMultilingualLabels(AggregatedApiResponse data, String query, String langParam) {
        boolean multiLang = isMultiLang(langParam);
        List<String> requestedLangs = parseLangs(langParam);

        for (Map<String, Object> item : data.getCollection()) {
            Object byLangRaw = item.get("labelByLang");
            boolean hasByLangMap = byLangRaw instanceof Map && !((Map<?, ?>) byLangRaw).isEmpty();

            if (multiLang && hasByLangMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> byLang = (Map<String, Object>) byLangRaw;
                String picked = pickLabel(byLang, query, requestedLangs);
                if (picked != null) {
                    item.put("label", picked);
                }
            } else {
                item.remove("labelByLang");
            }
        }
        return data;
    }

    private boolean isMultiLang(String lang) {
        if (lang == null || lang.isEmpty()) return false;
        return lang.contains(",") || lang.equalsIgnoreCase("all");
    }

    private List<String> parseLangs(String langParam) {
        if (langParam == null || langParam.isEmpty() || langParam.equalsIgnoreCase("all")) {
            return Collections.emptyList();
        }
        return Arrays.stream(langParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String pickLabel(Map<String, Object> byLang, String query, List<String> requestedLangs) {
        String qNorm = query == null ? "" : query.toLowerCase();

        // 1. langue dont la valeur contient la query
        if (!qNorm.isEmpty()) {
            for (String lang : requestedLangs) {
                String v = firstString(byLang.get(lang));
                if (v != null && v.toLowerCase().contains(qNorm)) {
                    return v;
                }
            }
        }

        // 2. 1ère langue demandée présente
        for (String lang : requestedLangs) {
            String v = firstString(byLang.get(lang));
            if (v != null) return v;
        }

        // 3. fallback 'none' (littéraux sans @lang tag)
        String noneVal = firstString(byLang.get("none"));
        if (noneVal != null) return noneVal;

        // 4. n'importe quelle valeur non-vide
        return byLang.values().stream()
                .map(this::firstString)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String firstString(Object v) {
        if (v == null) return null;
        if (v instanceof String s) {
            return s.isEmpty() ? null : s;
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first == null ? null : first.toString();
        }
        return null;
    }

    private AggregatedApiResponse enrichWithCategories(AggregatedApiResponse data) {
        for (Map<String, Object> item : data.getCollection()) {
            if (!isOntoPortalItem(item)) continue;
            Object portal = item.get("source_name");
            Object ontology = item.get("ontology");
            if (portal == null || ontology == null) continue;
            String acronym = extractAcronym(ontology.toString());
            List<String> categories = categoryCache.getCategories(portal.toString(), acronym);
            item.put("categories", categories);
        }
        return data;
    }

    private boolean isOntoPortalItem(Map<String, Object> item) {
        Object backendType = item.get("backend_type");
        return backendType != null && "ontoportal".equalsIgnoreCase(backendType.toString());
    }

    private String extractAcronym(String ontology) {
        int idx = ontology.lastIndexOf('/');
        return idx >= 0 ? ontology.substring(idx + 1) : ontology;
    }

    private AggregatedApiResponse filterByCategories(AggregatedApiResponse data, String categoriesParam) {
        if (categoriesParam == null || categoriesParam.isEmpty()) return data;
        Set<String> wanted = Arrays.stream(categoriesParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        if (wanted.isEmpty()) return data;

        List<Map<String, Object>> filtered = data.getCollection().stream()
                .filter(item -> !isOntoPortalItem(item) || itemMatchesCategories(item, wanted))
                .collect(Collectors.toList());
        data.setCollection(filtered);
        data.setTotalCount(filtered.size());
        return data;
    }

    @SuppressWarnings("unchecked")
    private boolean itemMatchesCategories(Map<String, Object> item, Set<String> wanted) {
        Object foundIn = item.get("found_in");
        if (foundIn instanceof List) {
            for (Object entry : (List<Object>) foundIn) {
                if (entry instanceof Map && hasMatchingCategory((List<?>) ((Map<String, Object>) entry).getOrDefault("categories", Collections.emptyList()), wanted)) {
                    return true;
                }
            }
        }
        Object topCategories = item.get("categories");
        return topCategories instanceof List && hasMatchingCategory((List<?>) topCategories, wanted);
    }

    private boolean hasMatchingCategory(List<?> categories, Set<String> wanted) {
        for (Object c : categories) {
            if (c != null && wanted.contains(c.toString().toLowerCase())) return true;
        }
        return false;
    }

    private AggregatedApiResponse deduplicateResults(AggregatedApiResponse data) {
        List<Map<String, Object>> collection = deduplicationService.deduplicate(data.getCollection());
        data.setCollection(collection);
        data.setTotalCount(collection.size());
        return data;
    }

    public AggregatedApiResponse suggestConcepts(
            String database,
            String id,
            String query,
            int offset,
            int size,
            CommonRequestParams params) {
        return suggestConcepts(database, id, query, offset, size, params, null, null, null);
    }

    public AggregatedApiResponse suggestConcepts(
            String database,
            String id,
            String query,
            int offset,
            int size,
            CommonRequestParams params,
            String collectionId,
            User currentUser,
            ApiAccessor accessor) {
        String endpoint = "suggest";
        TargetDbSchema targetDbSchema = params.getTargetDbSchema();
        TerminologyCollection collection = collectionService.getCurrentUserCollection(collectionId, currentUser);
        accessor = initAccessor(database, endpoint, accessor);
        accessor = applyCollection(accessor, collection, endpoint);
        accessor = applyLang(accessor, params.getLang());

        // TODO add ontology parameter as soon as https://github.com/ts4nfdi/api-gateway/issues/123 has been resolved.

        try {
            return accessor.get(query, "" + size, "" + offset)
                    .thenApply(data -> this.transformApiResponses(data, endpoint))
                    .thenApply(transformedData -> flattenResponseList(transformedData, params, collection))
                    .thenApply(data -> filterOutByCollection(collection, data))
                    .thenApply(this::enrichWithCategories)
                    .thenApply(this::deduplicateResults)
                    .thenApply(data -> filterByCategories(data, params.getCategories()))
                    .thenApply(data -> reIndexResults(query, data))
                    .thenApply(x -> transformJsonLd(x, params))
                    .thenApply(data -> transformForTargetDbSchema(data, targetDbSchema, endpoint, params.getLang()))
                    .get();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private AggregatedApiResponse sortResults(String query, AggregatedApiResponse data) {
        List<Map<String, Object>> collection = data.getCollection();
        collection = this.localIndexer.sortByCosineSimilarity(query.replace("*", ""), collection);
        data.setCollection(collection);
        return data;
    }

    private AggregatedApiResponse reIndexResults(String query, AggregatedApiResponse data) {
        List<Map<String, Object>> collection = data.getCollection();
        try {
            collection = this.localIndexer.reIndexResults(query.replace("*", ""), collection, logger);
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Error during re-indexing results", e);
        }
        data.setCollection(collection);
        return data;
    }

}
