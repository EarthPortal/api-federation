package org.semantics.apigateway.controller.ols;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.QueryParam;
import org.semantics.apigateway.artefacts.data.ArtefactsDataService;
import org.semantics.apigateway.artefacts.metadata.ArtefactsService;
import org.semantics.apigateway.artefacts.search.SearchService;
import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.responses.AggregatedApiResponse;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.auth.AuthService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Component
@RestController
@RequestMapping(value={"/ols/api", "/ols4/api"})
@Tag(name = "OLS")
public class Ols3Controller {
  
  private final SearchService searchService;
  private final ArtefactsService artefactsService;
  private final ArtefactsDataService artefactsDataService;
  private final AuthService authService;
  
  public Ols3Controller(SearchService searchService, ArtefactsService artefactsService, ArtefactsDataService artefactsDataService, AuthService authService) {
    this.searchService = searchService;
    this.artefactsService = artefactsService;
    this.artefactsDataService = artefactsDataService;
    this.authService = authService;
  }
  
  // TODO
  @CrossOrigin
  @GetMapping(value = {"/select", "/search"})
  public Object performDynFederatedSearchInOLSTargetDBSchema(@RequestParam Map<String, String> allParams) {
    String query;
    if (allParams.containsKey("q") || allParams.containsKey("query")) {
      if (allParams.containsKey("q")) {
        query = allParams.get("q");
      } else {
        query = allParams.get("query");
      }
    } else {
      query = "*";
    }
    
    AggregatedApiResponse response = searchService.performSearch(query + "*", allParams.get("ontology"), "ols", false);
    return response.getCollection().get(0);
  }
  
  @CrossOrigin
  @GetMapping("/suggest")
  public Object performTermSuggestInOLSTargetDBSchema(@RequestParam String q, @RequestParam(required = false, defaultValue = "") String ontology, @RequestParam(required = false, defaultValue = "") String database, @RequestParam(required = false, defaultValue = "10") Integer rows, @RequestParam(required = false, defaultValue = "0") Integer start, @ParameterObject CommonRequestParams params) {
    return searchService.suggestConcepts(database, ontology, q, start + 1, rows, params);
  }

  @CrossOrigin
  @GetMapping("/terms")
  public Object getAllTermsInOLSTargetDBSchema(@RequestParam(required = false, defaultValue = "0") Integer page, @QueryParam("iri") String iri, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @RequestParam(required = false) String collectionId) {
    if (iri != null) {
      return this.artefactsDataService.getArtefactTerms(database, iri, params, page + 1, null);
    }
    return this.artefactsDataService.getArtefactTerms(database, "", params, page + 1, null);
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}/terms")
  public Object getTermsInOLSTargetDBSchema(@PathVariable String onto, @RequestParam(required = false, defaultValue = "1") Integer page, @QueryParam("iri") String iri, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params) {
    if (iri != null) {
      return this.artefactsDataService.getArtefactTerm(database, onto, iri, params, null);
    }
    return this.artefactsDataService.getArtefactTerms(database, onto, params, page + 1, null);
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}")
  public Object getArtefactMetadataInOLSTargetDBSchema(@PathVariable String onto, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params) {
    return this.artefactsService.getArtefact(database, onto, params, null);
  }

  @CrossOrigin
  @GetMapping("/ontologies")
  public Object getArtefactsInOLSTargetDBSchema(@RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params,
                                                @Parameter(description = "Collection id to browse terminologies in") @RequestParam(required = false) String collectionId) {
    User user = authService.tryGetCurrentUser();
    return this.artefactsService.getArtefacts(database, params, collectionId, user, null);
  }
}

