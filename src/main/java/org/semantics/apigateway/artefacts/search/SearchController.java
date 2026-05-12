package org.semantics.apigateway.artefacts.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.semantics.apigateway.artefacts.metadata.ArtefactsService;
import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.TargetDbSchema;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.auth.AuthService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/")
@CrossOrigin
@Tag(name = "Search")
public class SearchController {
    private final SearchService searchService;
    private final ArtefactsService artefactsService;
    private final AuthService authService;

    public SearchController(SearchService searchService, ArtefactsService artefactsService, AuthService authService) {
        this.searchService = searchService;
        this.artefactsService = artefactsService;
        this.authService = authService;
    }


    @Operation(summary = "Search concepts across the federated catalogues.")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping(value = "/search")
    public Object search(
            @Parameter(description = "The text to search", example = "plant")
            @RequestParam String query,
            @Parameter(description = "Choose one or more OntoPortal sources to search (comma-separated). Use 'ontoportal' to search all portals at once. Available: agroportal, earthportal, biodivportal, ecoportal, lovportal, ontoportal-astro.", example = "ontoportal")
            @RequestParam String database,
            @ParameterObject CommonRequestParams params,
            @Parameter(description = "Collection id to search in", hidden = true)
            @RequestParam(required = false) String collectionId
    ) {
        User user = authService.tryGetCurrentUser();
        return searchService.performSearch(database, query, params, collectionId, user, null);
    }


    @Operation(summary = "Search all of the metadata in a catalogue.")
    @GetMapping(value = {"/search/metadata"})
    public Object searchMetadata(
            @Parameter(description = "The text to search", example = "plant")
            @RequestParam String query,
            @Parameter(description = "Choose one or more OntoPortal sources to search (comma-separated). Use 'ontoportal' to search all portals at once. Available: agroportal, earthportal, biodivportal, ecoportal, lovportal, ontoportal-astro.", example = "ontoportal")
            @RequestParam String database,
            @Parameter(description = "Filter results by ontology categories (comma-separated)")
            @RequestParam(required = false, defaultValue = "") String categories,
            @Parameter(description = "Transform the response result to a specific schema")
            @RequestParam(required = false) TargetDbSchema targetDbSchema
    ) {
        CommonRequestParams params = new CommonRequestParams();
        params.setTargetDbSchema(targetDbSchema);
        params.setCategories(categories);

        return artefactsService.searchMetadata(database, query, params, null);
    }
}
