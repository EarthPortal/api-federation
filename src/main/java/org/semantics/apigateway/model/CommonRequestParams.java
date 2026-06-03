package org.semantics.apigateway.model;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.ws.rs.QueryParam;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@Data
@Component
public class CommonRequestParams {

    @QueryParam("targetDbSchema")
    @Parameter(name = "targetDbSchema", in = ParameterIn.QUERY, description = "Transform the response result to a specific schema")
    private TargetDbSchema targetDbSchema;

    @Parameter(hidden = true)
    @QueryParam("showResponseConfiguration")
    private boolean showResponseConfiguration = false;

    @Parameter(hidden = true)
    @QueryParam("displayEmptyValues")
    private boolean displayEmptyValues = true;

    @QueryParam("disableCache")
    @Parameter(name = "disableCache", in = ParameterIn.QUERY, description = "Disable caching (not implemented yet)", hidden = true)
    private boolean disableCache = false;

    @QueryParam("lang")
    @Parameter(name="lang", in = ParameterIn.QUERY, description ="Language code to filter results (en, fr)")
    private String lang;

    @QueryParam("display")
    @Parameter(name = "display", in = ParameterIn.QUERY, description = "Choose the attribute to display in the results (coma seperated)",
            array = @ArraySchema(schema = @Schema(type = "string")), hidden = true)
    private String display = "";

    @QueryParam("categories")
    @Parameter(name = "categories", in = ParameterIn.QUERY, description = "Filter results by ontology categories (comma separated)")
    private String categories = "";


    public List<String> getDisplay() {
        List<String> result = new ArrayList<>();
//        result.add("iri"); // TODO remove this if the TSS no more use it and instead use the @id
        if (display != null && !display.isEmpty()) {
            result.addAll(Arrays.asList(display.split(",")));
        }
        return result;
    }
}
