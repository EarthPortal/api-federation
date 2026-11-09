package org.semantics.apigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@AutoConfigureMockMvc
@SpringBootTest
public class OpenApiDatabaseParamTest {

    private static final List<String> ONTOPORTAL_SOURCES = List.of(
            "agroportal", "earthportal", "biodivportal", "ecoportal", "lovportal", "ontoportal-astro");

    @Autowired
    private MockMvc mockMvc;

    private JsonNode databaseParam(JsonNode doc, String path) {
        for (JsonNode p : doc.path("paths").path(path).path("get").path("parameters")) {
            if ("database".equals(p.path("name").asText())) return p;
        }
        throw new AssertionError("no database parameter on " + path);
    }

    private List<String> values(JsonNode param) {
        List<String> values = new ArrayList<>();
        param.path("schema").path("items").path("enum").forEach(v -> values.add(v.asText()));
        return values;
    }

    @Test
    public void testDatabaseParamIsAMultiSelectOfLoadedSources() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        JsonNode doc = new ObjectMapper().readTree(json);

        JsonNode search = databaseParam(doc, "/search");
        assertThat(search.path("schema").path("type").asText()).isEqualTo("array");
        assertThat(search.path("style").asText()).isEqualTo("form");
        assertThat(search.path("explode").asBoolean(true)).isFalse();
        assertThat(search.path("required").asBoolean()).isTrue();
        assertThat(search.path("schema").path("default").get(0).asText()).isEqualTo("ontoportal");
        assertThat(search.has("example")).isFalse();
        // "ontoportal" first, then every source of databases.json (incl. non-OntoPortal ones)
        assertThat(values(search)).first().isEqualTo("ontoportal");
        assertThat(values(search)).containsAll(ONTOPORTAL_SOURCES).contains("nerc", "ebi", "agrovoc").hasSize(16);

        // The annotator only exists on OntoPortal sources
        JsonNode annotator = databaseParam(doc, "/annotator");
        List<String> expected = new ArrayList<>(List.of("ontoportal"));
        expected.addAll(ONTOPORTAL_SOURCES);
        assertThat(values(annotator)).isEqualTo(expected);
        assertThat(annotator.path("schema").path("default").get(0).asText()).isEqualTo("earthportal");
    }

    @Test
    public void testSwaggerUiPageGetsTheSourceSelectorScript() throws Exception {
        String html = mockMvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("tr[data-param-name='database'] select[multiple]");
        assertThat(html).contains("\"ontoportal\":\"all OntoPortal sources\"",
                "\"agroportal\":\"data.agroportal.eu\"", "\"ebi\":\"www.ebi.ac.uk\"");
        // Injected once, inside the page, before the end of <body>
        assertThat(html.indexOf("var labels")).isPositive().isLessThan(html.indexOf("</body>"));
        assertThat(html.indexOf("var labels")).isEqualTo(html.lastIndexOf("var labels"));
    }
}
