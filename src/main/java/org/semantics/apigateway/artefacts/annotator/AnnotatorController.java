package org.semantics.apigateway.artefacts.annotator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.auth.AuthService;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/")
@CrossOrigin
@Tag(name = "Annotator")
public class AnnotatorController {
    private final AnnotatorService annotatorService;
    private final AuthService authService;

    public AnnotatorController(AnnotatorService annotatorService, AuthService authService) {
        this.annotatorService = annotatorService;
        this.authService = authService;
    }

    @Operation(summary = "Annotate a text with concepts from the catalogue.")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping(value = "/annotator")
    public Object annotate(
            @Parameter(description = "The text to annotate", example = "soil temperature and water quality")
            @RequestParam String text,
            @Parameter(description = "Which source to query (e.g. earthportal).", example = "earthportal")
            @RequestParam String database,
            @Parameter(description = "Restrict to these ontology acronyms (comma-separated). Empty = all ontologies of the source.", example = "ENVTHES")
            @RequestParam(required = false, defaultValue = "") String ontologies
    ) {
        User user = authService.tryGetCurrentUser();
        return annotatorService.performAnnotation(database, text, ontologies, user);
    }
}
