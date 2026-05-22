package org.semantics.apigateway.artefacts.metadata;

import org.apache.lucene.queryparser.classic.ParseException;
import org.semantics.apigateway.artefacts.search.SearchLocalIndexerService;
import org.semantics.apigateway.collections.CollectionService;
import org.semantics.apigateway.collections.models.TerminologyCollection;
import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.SemanticArtefact;
import org.semantics.apigateway.model.responses.AggregatedApiResponse;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.AbstractEndpointService;
import org.semantics.apigateway.service.ApiAccessor;
import org.semantics.apigateway.service.JsonLdTransform;
import org.semantics.apigateway.service.ResponseTransformerService;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.semantics.apigateway.util.OntoPortalUtil;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;


@Service
public class ArtefactsService extends AbstractEndpointService {

    private final CollectionService collectionService;
    private final SearchLocalIndexerService localIndexer;
    private final ArtefactsDeduplicationService deduplicationService;
    private final OntoPortalUtil ontoPortalUtil;

    public ArtefactsService(ConfigurationLoader configurationLoader, CacheManager cacheManager, JsonLdTransform transform, ResponseTransformerService responseTransformerService, CollectionService collectionService, SearchLocalIndexerService localIndexer, ArtefactsDeduplicationService deduplicationService, OntoPortalUtil ontoPortalUtil) {
        super(configurationLoader, cacheManager, transform, responseTransformerService, SemanticArtefact.class);
        this.collectionService = collectionService;
        this.localIndexer = localIndexer;
        this.deduplicationService = deduplicationService;
        this.ontoPortalUtil = ontoPortalUtil;
    }


    public Object getArtefacts(String database, CommonRequestParams params, String collectionId, User currentUser, ApiAccessor accessor) {
        String endpoint = "resources";
        try {
            return
                    findAllArtefacts(database, params, collectionId, currentUser, accessor)
                            .thenApply(data -> filterByCategories(data, params.getCategories()))
                            .thenApply(this::deduplicateArtefacts)
                            .thenApply(data -> transformJsonLd(data, params))
                            .thenApply(data -> transformForTargetDbSchema(data, params.getTargetDbSchema(), endpoint)).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }


    public Object getArtefact(String database, String id, CommonRequestParams params, ApiAccessor accessor) {
        String endpoint = "resource_details";
        accessor = initAccessor(database, endpoint, accessor);
        try {
            return accessor.get(id)
                    .thenApply(data -> this.transformApiResponses(data, endpoint))
                    .thenApply(transformedData -> flattenResponseList(transformedData, params, null))
                    .thenApply(this::deduplicateArtefacts)
                    .thenApply(this::adjustSingleOrList)
                    .thenApply(x -> transformJsonLd(x, params))
                    .thenApply(data -> transformForTargetDbSchema(data, params.getTargetDbSchema(), endpoint, false))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    private AggregatedApiResponse adjustSingleOrList(AggregatedApiResponse data) {
        int size = data.getCollection() != null ? data.getCollection().size() : 0;
        data.setTotalCount(size);
        // 1 item after dedup
        // multiple items (ontoPortal canonical + non ontoPortal) → list
        data.setList(size != 1);
        return data;
    }


    public Object searchMetadata(String database, String query, CommonRequestParams params, ApiAccessor accessor) {
        String endpoint = "resources";
        return findAllArtefacts(database, params, null, null, accessor)
                .thenApply(data -> filterOutByQuery(query, data))
                .thenApply(data -> filterByCategories(data, params.getCategories()))
                .thenApply(this::deduplicateArtefacts)
                .thenApply(data -> reIndexResults(query, data))
                .thenApply(x -> transformJsonLd(x, params))
                .thenApply(data -> transformForTargetDbSchema(data, params.getTargetDbSchema(), endpoint));
    }

    private AggregatedApiResponse deduplicateArtefacts(AggregatedApiResponse data) {
        data.setCollection(deduplicationService.deduplicate(data.getCollection()));
        data.setTotalCount(data.getCollection().size());
        return data;
    }

    private AggregatedApiResponse reIndexResults(String query, AggregatedApiResponse data) {
        if (query == null || query.isEmpty()) return data;
        List<Map<String, Object>> collection = data.getCollection();
        try {
            collection = this.localIndexer.reIndexResults(query.replace("*", ""), collection, logger);
        } catch (IOException | ParseException e) {
            throw new RuntimeException("Error during re-indexing results", e);
        }
        data.setCollection(collection);
        return data;
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
                .filter(item -> !ontoPortalUtil.isOntoPortalItem(item) || itemMatchesCategories(item, wanted))
                .collect(Collectors.toList());
        data.setCollection(filtered);
        data.setTotalCount(filtered.size());
        return data;
    }

    @SuppressWarnings("unchecked")
    private boolean itemMatchesCategories(Map<String, Object> item, Set<String> wanted) {
        Object subject = item.get("subject");
        if (!(subject instanceof List)) return false;
        for (Object s : (List<Object>) subject) {
            if (s == null) continue;
            String url = s.toString().toLowerCase();
            for (String w : wanted) {
                if (url.endsWith("/categories/" + w) || url.contains(w)) return true;
            }
        }
        return false;
    }

    private AggregatedApiResponse filterOutByQuery(String query, AggregatedApiResponse data) {
        if (query == null || query.isEmpty()) {
            return data;
        }

        List<Map<String, Object>> filtered = data.getCollection().stream()
                .filter(item -> {
                    if(item == null) {
                        return false;
                    }

                    String label = item.get("label") == null ? "" : item.get("label").toString();
                    String iri = item.get("iri") == null ? "" : item.get("iri").toString();
                    boolean result = label.toLowerCase().contains(query.toLowerCase());
                    result = result || iri.toLowerCase().contains(query.toLowerCase());
                    return result;
                })
                .toList();

        data.setCollection(filtered);
        data.setTotalCount(filtered.size());
        return data;
    }

    private CompletableFuture<AggregatedApiResponse> findAllArtefacts(String database, CommonRequestParams params, String collectionId, User currentUser, ApiAccessor accessor) {
        String endpoint = "resources";
        TerminologyCollection collection = collectionService.getCurrentUserCollection(collectionId, currentUser);

        accessor = initAccessor(database, endpoint, accessor);
        accessor = applyCollection(accessor, collection, endpoint);

        return accessor.get()
                .thenApply(data -> this.transformApiResponses(data, endpoint))
                .thenApply(transformedData -> flattenResponseList(transformedData, params, collection))
                .thenApply(data -> filterOutByCollection(collection, data));
    }
}
