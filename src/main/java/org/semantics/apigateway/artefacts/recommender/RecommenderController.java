package org.semantics.apigateway.artefacts.recommender;

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
@Tag(name = "Recommender")
public class RecommenderController {
    private final RecommenderService recommenderService;
    private final AuthService authService;

    public RecommenderController(RecommenderService recommenderService, AuthService authService) {
        this.recommenderService = recommenderService;
        this.authService = authService;
    }

    @Operation(summary = "Recommend the most relevant ontologies for a text from the catalogue.")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping(value = "/recommender")
    public Object recommend(
            @Parameter(description = "The text or keywords to analyze.", example = "soil water temperature plant growth")
            @RequestParam String input,
            @Parameter(description = "Which source to query (e.g. earthportal).", example = "earthportal")
            @RequestParam String database,
            @Parameter(description = "Type of input: free text or list of keywords.",
                    schema = @Schema(allowableValues = {"text", "keywords"}, defaultValue = "text"))
            @RequestParam(name = "input_type", required = false, defaultValue = "text") String inputType,
            @Parameter(description = "Type of output: ranked ontologies or ranked ontology sets.",
                    schema = @Schema(allowableValues = {"ontologies", "sets"}, defaultValue = "ontologies"))
            @RequestParam(name = "output_type", required = false, defaultValue = "ontologies") String outputType,
            @Parameter(description = "Restrict to these ontology acronyms (comma-separated). Empty = all ontologies of the source.", example = "ENVTHES")
            @RequestParam(required = false, defaultValue = "") String ontologies,
            @Parameter(description = "Maximum number of ontologies per set. Used only when output_type = sets.",
                    schema = @Schema(allowableValues = {"2", "3", "4"}, defaultValue = "3"))
            @RequestParam(name = "max_elements_set", required = false, defaultValue = "3") String maxElementsSet,
            @Parameter(description = "Weight for the coverage score.")
            @RequestParam(name = "coverage_weight", required = false, defaultValue = "0.55") String coverageWeight,
            @Parameter(description = "Weight for the acceptance score.")
            @RequestParam(name = "acceptance_weight", required = false, defaultValue = "0.15") String acceptanceWeight,
            @Parameter(description = "Weight for the detail score.")
            @RequestParam(name = "detail_weight", required = false, defaultValue = "0.15") String detailWeight,
            @Parameter(description = "Weight for the specialization score.")
            @RequestParam(name = "specialization_weight", required = false, defaultValue = "0.15") String specializationWeight
    ) {
        User user = authService.tryGetCurrentUser();
        return recommenderService.performRecommendation(database, input, inputType, outputType, ontologies,
                maxElementsSet, coverageWeight, acceptanceWeight, detailWeight, specializationWeight, user);
    }
}
