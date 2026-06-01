package org.semantics.apigateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.semantics.apigateway.artefacts.metadata.ArtefactsDeduplicationService;
import org.semantics.apigateway.util.OntoPortalUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ArtefactsDeduplicationServiceTest {

    private ArtefactsDeduplicationService service;

    @BeforeEach
    public void setup() {
        OntoPortalUtil ontoPortalUtilMock = mock(OntoPortalUtil.class);

        when(ontoPortalUtilMock.getOntoPortalPortals())
                .thenReturn(Set.of("agroportal", "ecoportal", "biodivportal"));

        when(ontoPortalUtilMock.isOntoPortalItem(any())).thenAnswer(invocation -> {
            Map<String, Object> item = invocation.getArgument(0);
            return "ontoportal".equalsIgnoreCase((String) item.get("backend_type"));
        });

        service = new ArtefactsDeduplicationService(ontoPortalUtilMock);
    }

    @Test
    public void testPickCanonical_winnerIsMostReferredPortal() {
        Map<String, Object> agroItem = createItem("AGROVOC", "agroportal",
                "http://aims.fao.org/agrovoc/agrovoc.rdf.gz");
        Map<String, Object> ecoItem = createItem("AGROVOC", "ecoportal",
                "https://data.agroportal.lirmm.fr/ontologies/AGROVOC/download");
        Map<String, Object> biodivItem = createItem("AGROVOC", "biodivportal",
                "https://data.agroportal.lirmm.fr/ontologies/AGROVOC/download");

        List<Map<String, Object>> input = List.of(agroItem, ecoItem, biodivItem);

        List<Map<String, Object>> result = service.deduplicate(input);

        assertThat(result).hasSize(1);
        Map<String, Object> canonical = result.get(0);
        assertThat(canonical.get("source_name"))
                .as("canonical should be the most referred portal in pullLocations")
                .isEqualTo("agroportal");
    }


    @Test
    public void testPickCanonical_noClearWinner_picksFirstInGroup() {
        Map<String, Object> ecoItem = createItem("GEMET", "ecoportal",
                "https://www.eionet.europa.eu/gemet/latest/gemet.rdf.gz");
        Map<String, Object> biodivItem = createItem("GEMET", "biodivportal",
                "null");
        Map<String, Object> agroItem = createItem("GEMET", "agroportal",
                "https://www.eionet.europa.eu/gemet/latest/gemet.rdf.gz");

        List<Map<String, Object>> input = List.of(ecoItem, biodivItem, agroItem);

        List<Map<String, Object>> result = service.deduplicate(input);

        assertThat(result).hasSize(1);
        Map<String, Object> canonical = result.get(0);
        assertThat(canonical.get("source_name"))
                .as("no portal stands out → fallback: first item in the group wins")
                .isEqualTo("ecoportal");
    }

    private Map<String, Object> createItem(String acronym, String sourceName, String pullLocation) {
        Map<String, Object> item = new HashMap<>();
        item.put("backend_type", "ontoportal");
        item.put("short_form", acronym);
        item.put("source_name", sourceName);
        item.put("source_url", "http://" + sourceName + ".eu/ontologies/" + acronym);
        item.put("iri", "http://aims.fao.org/" + acronym.toLowerCase());
        item.put("pullLocation", pullLocation);
        return item;
    }
}
