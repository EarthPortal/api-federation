package org.semantics.apigateway.artefacts.metadata;

import org.semantics.apigateway.config.DatabaseConfig;
import org.semantics.apigateway.model.BackendType;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ArtefactsDeduplicationService {

    private final ConfigurationLoader configurationLoader;

    public ArtefactsDeduplicationService(ConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
    }

    private Set<String> ontoPortalPortals() {
        return configurationLoader.getDatabaseConfigs().stream()
                .filter(DatabaseConfig::isOntoPortal)
                .map(DatabaseConfig::getName)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    //main methode
    public List<Map<String, Object>> deduplicate(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        Set<String> portals = ontoPortalPortals();

        List<Map<String, Object>> ontoportalItems = new ArrayList<>();
        List<Map<String, Object>> otherItems = new ArrayList<>();

        //1
        for (Map<String, Object> item : results) {
            if (isOntoportal(item)) {
                ontoportalItems.add(item);
            } else {
                otherItems.add(item);
            }
        }

        //2 group by acronym
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> item : ontoportalItems) {
            String key = buildKey(item);
            if (key == null) {
                otherItems.add(item);
                continue;
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        //3 every group -> chose canonical + found in
        List<Map<String, Object>> deduped = new ArrayList<>();
        for (List<Map<String, Object>> group : groups.values()) {
            Map<String, Object> source = group.size() == 1 ? group.get(0) : pickCanonical(group, portals);
            Map<String, Object> canonical = new LinkedHashMap<>(source);

            List<Map<String, Object>> foundIn = buildFoundInList(group, portals);
            if (foundIn.size() > 1) {
                canonical.put("found_in", foundIn);
            }
            deduped.add(canonical);
        }

        deduped.addAll(otherItems);
        return deduped;
    }

    private Map<String, Object> pickCanonical(List<Map<String, Object>> group, Set<String> portals) {
        Map<String, Integer> portalCounts = new HashMap<>();
        for (Map<String, Object> item : group) {
            Object pullLoc = item.get("pullLocation");
            if (pullLoc == null) continue;
            String pl = pullLoc.toString().toLowerCase();
            for (String portal : portals) {
                if (pl.contains(portal)) {
                    portalCounts.merge(portal, 1, Integer::sum);
                }
            }
        }

        if (portalCounts.isEmpty()) {
            return group.get(0);
        }

        String winner = portalCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (winner != null) {
            for (Map<String, Object> item : group) {
                Object srcName = item.get("source_name");
                if (srcName != null && winner.equalsIgnoreCase(srcName.toString())) {
                    return item;
                }
            }
        }
        return group.get(0);
    }

    private List<Map<String, Object>> buildFoundInList(List<Map<String, Object>> group, Set<String> portals) {
        List<Map<String, Object>> list = new ArrayList<>();
        Set<String> seenPortals = new HashSet<>();
        for (Map<String, Object> item : group) {
            Map<String, Object> entry = buildFoundInEntry(item, portals);
            if (entry == null) continue;
            String portal = (String) entry.get("source_name");
            if (portal != null && seenPortals.add(portal)) {
                list.add(entry);
            }
        }
        return list;
    }

    private Map<String, Object> buildFoundInEntry(Map<String, Object> item, Set<String> portals) {
        Object sourceName = item.get("source_name");
        if (sourceName == null) return null;
        String portal = sourceName.toString().toLowerCase();
        if (!portals.contains(portal)) return null;

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("source_name", portal);
        entry.put("source_url", item.get("source_url"));
        entry.put("iri", item.get("iri"));
        Object subject = item.get("subject");
        entry.put("categories", subject instanceof List ? subject : Collections.emptyList());
        return entry;
    }

    private String buildKey(Map<String, Object> item) {
        Object acronym = item.get("short_form");
        if (acronym == null) return null;
        return acronym.toString().toUpperCase();
    }

    private boolean isOntoportal(Map<String, Object> item) {
        Object backendType = item.get("backend_type");
        return backendType != null && BackendType.ontoportal.toString().equalsIgnoreCase(backendType.toString());
    }
}
