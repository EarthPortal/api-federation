package org.semantics.apigateway.artefacts.search;

import org.semantics.apigateway.util.OntoPortalUtil;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SearchDeduplicationService {

    private final OntoPortalUtil ontoPortalUtil;

    public SearchDeduplicationService(OntoPortalUtil ontoPortalUtil) {
        this.ontoPortalUtil = ontoPortalUtil;
    }

    public List<Map<String, Object>> deduplicate(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        Set<String> portals = ontoPortalUtil.getOntoPortalPortals();

        List<Map<String, Object>> ontoportalItems = new ArrayList<>();
        List<Map<String, Object>> otherItems = new ArrayList<>();

        for (Map<String, Object> item : results) {
            if (ontoPortalUtil.isOntoPortalItem(item)) {
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
                copy.put("found_in", buildFoundInList(item, portals));
                copy.remove("categories");
                grouped.put(key, copy);
            } else {
                List<Map<String, Object>> foundIn = (List<Map<String, Object>>) existing.get("found_in");
                Map<String, Object> entry = buildFoundInEntry(item, portals);
                if (entry != null && !containsPortal(foundIn, (String) entry.get("portal"))) {
                    foundIn.add(entry);
                }
            }
        }

        for (Map<String, Object> item : grouped.values()) {
            List<Map<String, Object>> foundIn = (List<Map<String, Object>>) item.get("found_in");
            if (foundIn != null && foundIn.size() <= 1) {
                item.remove("found_in");
                if (foundIn != null && !foundIn.isEmpty()) {
                    item.put("categories", foundIn.get(0).get("categories"));
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(grouped.values());
        result.addAll(otherItems);
        return result;
    }

    private Map<String, Object> buildFoundInEntry(Map<String, Object> item, Set<String> portals) {
        Object sourceName = item.get("source_name");
        Object uiLink = item.get("source_url");
        if(sourceName == null)
            return null;
        String portal = sourceName.toString().toLowerCase();
        if(!portals.contains(portal))
            return null;
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("portal", portal);
        p.put("ui_link", uiLink == null ? "" : uiLink.toString());
        Object categories = item.get("categories");
        p.put("categories", categories instanceof List ? categories : Collections.emptyList());
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

    private boolean containsPortal(List<Map<String, Object>> foundIn, String portal) {
        if(foundIn == null)
            return false;
        for (Map<String, Object> e : foundIn){
            if(portal.equalsIgnoreCase((String) e.get("portal")))
                return true;
        }
        return false;
    }

    private Object buildFoundInList(Map<String, Object> item, Set<String> portals) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> p = buildFoundInEntry(item, portals);
        if(p != null)
            list.add(p);
        return list;
    }


}
