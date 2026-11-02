package org.semantics.apigateway.util;

import org.semantics.apigateway.config.DatabaseConfig;
import org.semantics.apigateway.model.BackendType;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class OntoPortalUtil {

    private final ConfigurationLoader configurationLoader;

    public OntoPortalUtil(ConfigurationLoader configurationLoader) {
        this.configurationLoader = configurationLoader;
    }

    // check if the item is ontoportal type
    public boolean isOntoPortalItem(Map<String, Object> item) {
        Object backendType = item.get("backend_type");
        return backendType != null
                && BackendType.ontoportal.toString().equalsIgnoreCase(backendType.toString());
    }

    // return list porta    l of ontoportal
    public Set<String> getOntoPortalPortals() {
        return configurationLoader.getDatabaseConfigs().stream()
                .filter(DatabaseConfig::isOntoPortal)
                .map(DatabaseConfig::getName)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }
}
