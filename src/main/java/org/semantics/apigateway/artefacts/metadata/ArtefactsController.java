package org.semantics.apigateway.artefacts.metadata;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.TargetDbSchema;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.auth.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;


@RestController
@RequestMapping("/")
@Tag(name = "Artefacts / Metadata")
@CrossOrigin
public class ArtefactsController {
    private final ArtefactsService artefactsService;
    private final AuthService authService;

    public ArtefactsController(ArtefactsService artefactsService, AuthService authService) {
        this.artefactsService = artefactsService;
        this.authService = authService;
    }


    @GetMapping("/artefacts")
    @Operation(summary = "Get information about all semantic artefacts.")
    @SecurityRequirement(name = "BearerAuth")
    public Object getArtefacts(
            @Parameter(description = "Which source to query. Pass a single source (e.g. agroportal), several separated by commas (e.g. agroportal,ecoportal), or ontoportal to query all OntoPortal sources at once. Available sources: agroportal, earthportal, biodivportal, ecoportal, lovportal, ontoportal-astro.", example = "ontoportal")
            @RequestParam String database,
            @Parameter(description = "Transform the response result to a specific schema")
            @RequestParam(required = false) TargetDbSchema targetDbSchema,
            @Parameter(description = "Filter results by ontology categories (comma-separated)")
            @RequestParam(required = false, defaultValue = "") String categories,
            @Parameter(description = "Collection id to browse terminologies in", hidden = true)
            @RequestParam(required = false) String collectionId
    ) throws ExecutionException, InterruptedException {
        CommonRequestParams params = new CommonRequestParams();
        params.setTargetDbSchema(targetDbSchema);
        params.setCategories(categories);
        User user = authService.tryGetCurrentUser();
        return this.artefactsService.getArtefacts(database, params, collectionId, user, null);
    }

    @GetMapping("/artefacts/{id}")
    @Operation(summary = "Get information about a semantic artefact.")
    public Object getArtefact(
            @Parameter(description = "Acronym of the artefact (e.g. GEMET, ACTRIS, AGROVOC)", example = "GEMET")
            @PathVariable("id") String id,
            @Parameter(description = "Which source to query. Pass a single source (e.g. agroportal), several separated by commas (e.g. agroportal,ecoportal), or ontoportal to query all OntoPortal sources at once. Available sources: agroportal, earthportal, biodivportal, ecoportal, lovportal, ontoportal-astro.", example = "ontoportal")
            @RequestParam String database,
            @Parameter(description = "Transform the response result to a specific schema")
            @RequestParam(required = false) TargetDbSchema targetDbSchema) {
        CommonRequestParams params = new CommonRequestParams();
        params.setTargetDbSchema(targetDbSchema);
        return this.artefactsService.getArtefact(database, id, params, null);
    }

}
