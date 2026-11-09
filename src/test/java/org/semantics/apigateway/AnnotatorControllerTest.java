package org.semantics.apigateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.semantics.apigateway.artefacts.annotator.AnnotatorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
public class AnnotatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnnotatorService annotatorService;

    @Test
    public void testPostSendsTheBodyAsTextWithTheSameOptions() throws Exception {
        when(annotatorService.performAnnotation(anyString(), anyString(), anyString(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyString(), any())).thenReturn(Map.of("totalCount", 0));
        String text = "température du sol\net qualité de l'eau";

        // No charset in the Content-Type, as Swagger UI sends it: must still be read as UTF-8
        mockMvc.perform(post("/annotator").param("database", "earthportal").param("ontologies", "ENVTHES")
                        .param("longest_only", "true").param("class_hierarchy_max_level", "2")
                        .contentType(MediaType.TEXT_PLAIN).content(text.getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk());

        verify(annotatorService).performAnnotation(eq("earthportal"), eq(text), eq("ENVTHES"), eq(false), eq(false),
                eq(false), eq(false), eq(true), eq("2"), any());
    }

    @Test
    public void testSwaggerShowsPostWithATextBody() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        JsonNode post = new ObjectMapper().readTree(json).path("paths").path("/annotator").path("post");

        JsonNode body = post.path("requestBody");
        assertThat(body.path("required").asBoolean()).isTrue();
        assertThat(body.path("content").path("text/plain").path("schema").path("type").asText()).isEqualTo("string");

        boolean hasTextParam = false;
        for (JsonNode p : post.path("parameters")) {
            if ("text".equals(p.path("name").asText())) hasTextParam = true;
            if ("database".equals(p.path("name").asText())) {
                assertThat(p.path("schema").path("type").asText()).isEqualTo("array");
                assertThat(p.path("schema").path("items").path("enum").get(0).asText()).isEqualTo("ontoportal");
            }
        }
        assertThat(hasTextParam).isFalse();
    }
}
