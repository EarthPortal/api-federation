package org.semantics.apigateway.artefacts.annotator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @RequestParam(required = false, defaultValue = "") String ontologies,
            @Parameter(description = "Expand the mappings of the annotated classes.")
            @RequestParam(name = "expand_mappings", required = false, defaultValue = "false") boolean expandMappings,
            @Parameter(description = "Exclude numbers from the annotations.")
            @RequestParam(name = "exclude_numbers", required = false, defaultValue = "false") boolean excludeNumbers,
            @Parameter(description = "Exclude synonyms from the annotations.")
            @RequestParam(name = "exclude_synonyms", required = false, defaultValue = "false") boolean excludeSynonyms,
            @Parameter(description = "Include ancestors up to this level.",
                    schema = @Schema(allowableValues = {"None", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "All"}, defaultValue = "None"))
            @RequestParam(name = "class_hierarchy_max_level", required = false, defaultValue = "None") String classHierarchyMaxLevel
    ) {
        User user = authService.tryGetCurrentUser();
        return annotatorService.performAnnotation(database, text, ontologies, expandMappings, excludeNumbers, excludeSynonyms, classHierarchyMaxLevel, user);
    }
}
