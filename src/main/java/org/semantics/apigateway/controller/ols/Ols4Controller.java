package org.semantics.apigateway.controller.ols;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.QueryParam;
import org.apache.commons.lang3.NotImplementedException;
import org.semantics.apigateway.api.OlsV2Transformer;
import org.semantics.apigateway.artefacts.data.ArtefactsDataService;
import org.semantics.apigateway.artefacts.metadata.ArtefactsService;
import org.semantics.apigateway.artefacts.search.SearchService;
import org.semantics.apigateway.artefacts.tree.ArtefactsDataTreeService;
import org.semantics.apigateway.controller.ols.model.CommonOLS4Params;
import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.responses.AggregatedApiResponse;
import org.semantics.apigateway.service.auth.AuthService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@Component
@RestController
@RequestMapping(value={"/ols/api/v2", "/ols4/api/v2"})
@Tag(name = "OLS 4")
@SuppressWarnings("unused")
public class Ols4Controller {
  
  private final ArtefactsService artefactsService;
  private final ArtefactsDataService artefactsDataService;
  private final AuthService authService;
  private final ArtefactsDataTreeService treeService;
  
  private OlsV2Transformer olsV2Transformer = new OlsV2Transformer(); // TODO This breaks decoupling. Better pass original request through the services, so that we know how to construct the response in the transformers.
  
  public Ols4Controller(SearchService searchService, ArtefactsService artefactsService, ArtefactsDataService artefactsDataService, AuthService authService, ArtefactsDataTreeService treeService) {
    this.artefactsService = artefactsService;
    this.artefactsDataService = artefactsDataService;
    this.authService = authService;
    this.treeService = treeService;
  }
  
  @CrossOrigin
  @GetMapping("/ontologies")
  public Object getAllOntologiesInOLSTargetDBSchema(@RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @RequestParam(required = false) String collectionId) {
    return artefactsService.getArtefacts(database, params, collectionId, authService.tryGetCurrentUser(), null);
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}")
  public Object getOntologyInOLSTargetDBSchema(@PathVariable String onto, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params) {
    return artefactsService.getArtefact(database, onto, params, null);
  }
  
  @CrossOrigin
  @GetMapping("/stats")
  public Object getStatsInOLSTargetDBSchema(@ParameterObject CommonRequestParams params) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/individuals")
  public Object getAllIndividualsForOntologyInOLSTargetDBSchema(@PathVariable String onto, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @QueryParam("iri") String iri) {
    if (iri == null) return artefactsDataService.getArtefactIndividuals(database, onto, params, pageable.getPageNumber() + 1, null);
    AggregatedApiResponse response = (AggregatedApiResponse) artefactsDataService.getArtefactIndividual(database, onto, iri, params, null);
    return olsV2Transformer.constructResponse(response.getCollection(), "concepts", true, true, 1, response.getCollection().size());
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}/individuals/{individual}")
  public Object getIndividualForOntologyInOLSTargetDBSchema(@PathVariable String onto, @PathVariable String individual, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params) {
    return artefactsDataService.getArtefactIndividual(database, onto, individual, params, null);
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}/individuals")
  public Object getAllIndividualsForClassInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/individuals")
  public Object getAllIndividualsInOLSTargetDBSchema(@RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @RequestParam(required = false) String collectionId, @QueryParam("iri") String iri) {
    if (iri != null) {
      return this.artefactsDataService.getArtefactIndividuals(database, iri, params, pageable.getPageNumber() + 1, null);
    }
    return this.artefactsDataService.getArtefactIndividuals(database, "", params, pageable.getPageNumber() + 1, null);
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}/entities")
  public Object getAllEntitiesForOntologyInOLSTargetDBSchema(@PathVariable String onto, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @QueryParam("iri") String iri) {
    if (iri == null) return artefactsDataService.getArtefactTerms(database, onto, params, pageable.getPageNumber() + 1, null);
    AggregatedApiResponse response = (AggregatedApiResponse) artefactsDataService.getArtefactTerm(database, onto, iri, params, null);
    return olsV2Transformer.constructResponse(response.getCollection(), "concepts", true, true, 1, response.getCollection().size());
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}/entities/{entity}")
  public Object getEntityInOLSTargetDBSchema(@PathVariable String onto, @PathVariable String entity, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params) {
    return artefactsDataService.getArtefactTerm(database, onto, entity, params, null);
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/entities/{entity}/relatedFrom")
  public Object getEntityRelatedFromInOLSTargetDBSchema(@PathVariable String onto, @PathVariable String entity, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/entities")
  public Object getAllEntitiesInOLSTargetDBSchema(@RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @QueryParam("iri") String iri) {
    // TODO Is there a way to run a federated query over all endpoints and their respective artifacts for all entities? Improbable, solely for performance reasons.
    if (iri != null) {
      return this.artefactsDataService.getArtefactTerms(database, iri, params, pageable.getPageNumber() + 1, null);
    }
    return this.artefactsDataService.getArtefactTerms(database, "", params, pageable.getPageNumber() + 1, null);
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/individuals/{individual}/ancestors")
  public Object getIndividualAncestorsInOLSTargetDBSchema(@PathVariable String onto, @PathVariable String individual, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes")
  public Object getClassesInOLSTargetDBSchema(@PathVariable String onto, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @QueryParam("iri") String iri) {
    if (iri == null) return artefactsDataService.getArtefactTerms(database, onto, params, pageable.getPageNumber() + 1, null);
    AggregatedApiResponse response = (AggregatedApiResponse) artefactsDataService.getArtefactTerm(database, onto, iri, params, null);
    return olsV2Transformer.constructResponse(response.getCollection(), "concepts", true, true, 1, response.getCollection().size());
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}")
  public Object getClassInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params) {
    return artefactsDataService.getArtefactTerm(database, onto, clazz, params, null);
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}/relatedFrom")
  public Object getClassRelatedFromInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}/hierarchicalDescendants")
  public Object getClassHierarchicalDescendantsInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}/hierarchicalChildren")
  public Object getClassHierarchicalChildrenInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}/hierarchicalAncestors")
  public Object getClassHierarchicalAncestorsInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}/descendants")
  public Object getClassDecendantsInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}/children")
  public Object getClassChildrenInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    return treeService.getChildren(database, onto, clazz, params, pageable.getPageNumber() + 1, null);
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/classes/{class}/ancestors")
  public Object getClassAncestorsInOLSTargetDBSchema(@PathVariable String onto, @PathVariable("class") String clazz, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/classes")
  public Object getAllClassesInOLSTargetDBSchema(@RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @QueryParam("iri") String iri) {
    if (iri != null) {
      return this.artefactsDataService.getArtefactTerms(database, iri, params, pageable.getPageNumber() + 1, null);
    }
    return this.artefactsDataService.getArtefactTerms(database, "", params, pageable.getPageNumber() + 1, null);
  }

  @CrossOrigin
  @GetMapping("/properties")
  public Object getAllPropertiesInOLSTargetDBSchema(@RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @QueryParam("iri") String iri) {
    if (iri != null) {
      return this.artefactsDataService.getArtefactProperties(database, iri, params, pageable.getPageNumber() + 1, null);
    }
    return this.artefactsDataService.getArtefactProperties(database, "", params, pageable.getPageNumber() + 1, null);
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}/properties")
  public Object getPropertiesForOntologyInOLSTargetDBSchema(@PathVariable String onto, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable, @QueryParam("iri") String iri) {
    if (iri == null) return artefactsDataService.getArtefactProperties(database, onto, params, pageable.getPageNumber() + 1, null);
    AggregatedApiResponse response = (AggregatedApiResponse) artefactsDataService.getArtefactProperty(database, onto, iri, params, null);
    return olsV2Transformer.constructResponse(response.getCollection(), "concepts", true, true, 1, response.getCollection().size());
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}/properties/{property}")
  public Object getPropertyInOLSTargetDBSchema(@PathVariable String onto, @PathVariable String property, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params) {
    return  artefactsDataService.getArtefactProperty(database, onto, property, params, null);
  }

  @CrossOrigin
  @GetMapping("/ontologies/{onto}/properties/{property}/children")
  public Object getPropertyChildenInOLSTargetDBSchema(@PathVariable String onto, @PathVariable String property, @RequestParam(required = false, defaultValue = "") String database, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    return treeService.getChildren(database, onto, property, params, pageable.getPageNumber() + 1, null);
  }
  
  @CrossOrigin
  @GetMapping("/ontologies/{onto}/properties/{property}/ancestors")
  public Object getProperyAncestorsInOLSTargetDBSchema(@PathVariable String onto, @PathVariable String property, @ParameterObject CommonRequestParams params, @ParameterObject CommonOLS4Params ols4Params, @PageableDefault(page = 0, size = 20) Pageable pageable) {
    throw new NotImplementedException();
  }
  
  @CrossOrigin
  @GetMapping("/defined-fields")
  public Object getDefinedFieldsInOLSTargetDBSchema(@ParameterObject CommonRequestParams params) {
    throw new NotImplementedException();
  }
}
