package org.semantics.apigateway.artefacts.data;

import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.RDFResource;
import org.semantics.apigateway.service.AbstractEndpointService;
import org.semantics.apigateway.service.ApiAccessor;
import org.semantics.apigateway.service.JsonLdTransform;
import org.semantics.apigateway.service.ResponseTransformerService;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;


@Service
public class ArtefactsDataService extends AbstractEndpointService {


    public ArtefactsDataService(ConfigurationLoader configurationLoader, CacheManager cacheManager, JsonLdTransform transform, ResponseTransformerService responseTransformerService) {
        super(configurationLoader, cacheManager, transform, responseTransformerService, RDFResource.class);
    }

    public Object getArtefactTerm(String database, String id, String uri, CommonRequestParams params, ApiAccessor accessor) {
        return findUri(database, id, uri, "concept_details", params, accessor);
    }

    public Object getArtefactTerms(String database, String id, CommonRequestParams params, Integer page, ApiAccessor accessor) {
        return paginatedList(database, id, "concepts", params, page, accessor);
    }

    public Object getArtefactProperty(String database, String id, String uri, CommonRequestParams params, ApiAccessor accessor) {
        return findUri(database, id, uri, "property_details", params, accessor);
    }

    public Object getArtefactProperties(String database, String id, CommonRequestParams params, Integer page, ApiAccessor accessor) {
        return paginatedList(database, id, "properties", params, page, accessor);
    }

    public Object getArtefactIndividual(String database, String id, String uri, CommonRequestParams params, ApiAccessor accessor) {
        return findUri(database, id, uri, "individual_details", params, accessor);
    }

    public Object getArtefactIndividuals(String database, String id, CommonRequestParams params, Integer page, ApiAccessor accessor) {
        return paginatedList(database, id, "individuals", params, page, accessor);
    }


    public Object getArtefactSchemes(String database, String id, CommonRequestParams params, Integer page, ApiAccessor accessor) {
        return paginatedList(database, id, "schemes", params, page, accessor);
    }

    public Object getArtefactScheme(String database, String id, String uri, CommonRequestParams params, ApiAccessor accessor) {
        return findUri(database, id, uri, "scheme_details", params, accessor);
    }

    public Object getArtefactCollections(String database, String id, CommonRequestParams params, Integer page, ApiAccessor accessor) {
        return paginatedList(database, id, "collections", params, page, accessor);
    }

    public Object getArtefactCollection(String database, String id, String uri, CommonRequestParams params, ApiAccessor accessor) {
        return findUri(database, id, uri, "collection_details", params, accessor);
    }
}
