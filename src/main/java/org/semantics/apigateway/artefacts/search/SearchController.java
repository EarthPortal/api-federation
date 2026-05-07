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


    @Operation(summary = "Search all of the content in a catalogue.")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping(value = {"/search", "/search/content"})
    public Object search(
            @Parameter(description = "The text to search", example = "plant")
            @RequestParam String query,
            @ParameterObject CommonRequestParams params,
            @Parameter(description = "Collection id to search in")
            @RequestParam(required = false) String collectionId
    ) {
        User user = authService.tryGetCurrentUser();
        return searchService.performSearch(query, params, collectionId, user, null);
    }


    @Operation(summary = "Search all of the metadata in a catalogue.")
    @GetMapping(value = {"/search/metadata"})
    public Object searchMetadata(
            @Parameter(description = "The text to search", example = "plant")
            @RequestParam String query,
            @Parameter(description = "Choose on which databases of backend type to run the search")
            @RequestParam(required = false, defaultValue = "") String database,
            @Parameter(description = "Transform the response result to a specific schema")
            @RequestParam(required = false) TargetDbSchema targetDbSchema,
            @Parameter(description = "Choose the attribute to display in the results (comma separated)")
            @RequestParam(required = false, defaultValue = "") String display,
            @RequestParam(required = false) boolean showResponseConfiguration,
            @RequestParam(required = false) boolean displayEmptyValues
    ) {
        CommonRequestParams params = new CommonRequestParams();
        params.setDatabase(database);
        params.setTargetDbSchema(targetDbSchema);
        params.setDisplay(display);
        params.setShowResponseConfiguration(showResponseConfiguration);
        params.setDisplayEmptyValues(displayEmptyValues);
        return artefactsService.searchMetadata(query, params, null);
    }
}
