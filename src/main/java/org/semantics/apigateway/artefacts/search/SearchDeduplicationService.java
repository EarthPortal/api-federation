package org.semantics.apigateway.artefacts.search;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SearchDeduplicationService {

    private static final Set<String> ONTOPORTAL_PORTALS = new HashSet<>(Arrays.asList("earthportal", "agroportal", "ecoportal", "biodivportal", "lovportal"));

    public List<Map<String, Object>> deduplicate(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        List<Map<String, Object>> ontoportalItems = new ArrayList<>();
        List<Map<String, Object>> otherItems = new ArrayList<>();

        for (Map<String, Object> item : results) {
            if (isOntoportal(item)) {
                ontoportalItems.add(item);
            } else {
                otherItems.add(item);
            }
        }

        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();

        for (Map<String, Object> item : ontoportalItems) {
            String key = buildKey(item);
            if (key == null) {
                otherItems.add(item);
                continue;
            }

            Map<String, Object> existing = grouped.get(key);
            if (existing == null) {
                Map<String, Object> copy = new LinkedHashMap<>(item);
                copy.put("found_in", buildFoundInList(item));
                grouped.put(key, copy);
            } else {
                List<Map<String, String>> foundIn = (List<Map<String, String>>) existing.get("found_in");
                Map<String, String> entry = buildFoundInEntry(item);
                if (entry != null && !containsPortal(foundIn, entry.get("portal"))) {
                    foundIn.add(entry);
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(grouped.values());
        result.addAll(otherItems);
        return result;
    }

    private Map<String, String> buildFoundInEntry(Map<String, Object> item) {
        Object sourceName = item.get("source_name");
        Object uiLink = item.get("source_url");
        if(sourceName == null)
            return null;
        String portal = sourceName.toString().toLowerCase();
        if(!ONTOPORTAL_PORTALS.contains(portal))
            return null;
        Map<String, String> p = new LinkedHashMap<>();
        p.put("portal", portal);
        p.put("ui_link", uiLink == null ? "" : uiLink.toString());

        return p;
    }

    private String buildKey(Map<String, Object> item) {
        Object iri = item.get("iri");
        if(iri == null)
            return null;
        Object ontology = item.get("ontology");
        String acronym = ontology == null ? "" : extractAcronym(ontology.toString());

        return iri.toString()+"|"+acronym.toUpperCase();
    }

    private String extractAcronym(String ontology){
        if(ontology == null || ontology.isEmpty())
            return "";
        int idx = ontology.lastIndexOf('/');
        return idx >= 0 ? ontology.substring(idx+1) : ontology;
    }

    private boolean isOntoportal(Map<String, Object> item) {
        Object backendType = item.get("backend_type");
        if(backendType == null )
            return false;
        return "ontoportal".equalsIgnoreCase(backendType.toString());
    }

    private boolean containsPortal(List<Map<String, String>> foundIn, String portal) {
        if(foundIn == null)
            return false;
        for (Map<String, String> e : foundIn){
            if(portal.equalsIgnoreCase(e.get("portal")))
                return true;
        }
        return false;
    }

    private Object buildFoundInList(Map<String, Object> item) {
        List<Map<String, String>> list = new ArrayList<>();
        Map<String, String> p = buildFoundInEntry(item);
        if(p != null)
            list.add(p);
        return list;
    }


}
