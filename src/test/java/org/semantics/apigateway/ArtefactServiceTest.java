package org.semantics.apigateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.semantics.apigateway.artefacts.metadata.ArtefactsService;
import org.semantics.apigateway.model.CommonRequestParams;
import org.semantics.apigateway.model.SemanticArtefact;
import org.semantics.apigateway.model.responses.AggregatedApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ArtefactServiceTest extends ApplicationTestAbstract {

    @Autowired
    private ArtefactsService artefactsService;


    @BeforeEach
    public void setup() {
        mockApiAccessor("artefact", artefactsService.getAccessor());
        this.responseClass = SemanticArtefact.class;
    }


    @Test
    public void testGetArtefacts(){
        CommonRequestParams commonRequestParams = new CommonRequestParams();
        AggregatedApiResponse response = (AggregatedApiResponse) artefactsService.getArtefact("ontoportal", "AGROVOC", commonRequestParams, apiAccessor);
        assertMapEquality(response, createOntoportalAgrovocFixture());
    }


    @Test
    public void testGetArtefactsSkosmos(){
        CommonRequestParams commonRequestParams = new CommonRequestParams();
        AggregatedApiResponse response = (AggregatedApiResponse) artefactsService.getArtefact("skosmos", "AGROVOC", commonRequestParams, apiAccessor);
        assertMapEquality(response, createSkosmosAgrovocFixture());
    }

    @Test
    public void testGetArtefactsOls() {
        CommonRequestParams commonRequestParams = new CommonRequestParams();
        AggregatedApiResponse response = (AggregatedApiResponse) artefactsService.getArtefact("ols", "AGROVOC", commonRequestParams, apiAccessor);
        assertMapEquality(response, createOlsAgrovocFixture());
    }

    @Test
    public void testGetArtefactGND() {
        CommonRequestParams commonRequestParams = new CommonRequestParams();
        AggregatedApiResponse response = (AggregatedApiResponse) artefactsService.getArtefact("gnd", "gnd", commonRequestParams, apiAccessor);
        assertMapEquality(response, createGndFixture());
    }


    @Test
    public void testGetArtefactJSkos() {
        CommonRequestParams commonRequestParams = new CommonRequestParams();
        AggregatedApiResponse response = (AggregatedApiResponse) artefactsService.getArtefact("jskos", "gender", commonRequestParams, apiAccessor);
        assertMapEquality(response, createDanteFixture());
    }


    @Test
    public void testGetArtefactJSkos2() {
        CommonRequestParams commonRequestParams = new CommonRequestParams();
        AggregatedApiResponse response = (AggregatedApiResponse) artefactsService.getArtefact("jskos2", "EuroVoc", commonRequestParams, apiAccessor);
        assertMapEquality(response, createColiConc());
    }
}
