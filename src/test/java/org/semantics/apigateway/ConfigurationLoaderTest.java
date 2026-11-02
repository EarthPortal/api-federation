package org.semantics.apigateway;

import org.junit.jupiter.api.Test;
import org.semantics.apigateway.service.ApiAccessor;
import org.semantics.apigateway.service.configuration.ConfigurationLoader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConfigurationLoaderTest {

    private static final List<String> ONTOPORTAL_KEYS = List.of("AGROPORTAL_API_KEY", "EARTHPORTAL_API_KEY",
            "BIODIVPORTAL_API_KEY", "ECOPORTAL_API_KEY", "ONTOPORTAL_ASTRO_API_KEY");

    private ConfigurationLoader load(MockEnvironment environment) {
        ConfigurationLoader loader = new ConfigurationLoader(new DefaultResourceLoader(), environment);
        ReflectionTestUtils.setField(loader, "databasesConfigLocation", "classpath:databases.ontoportal.json");
        loader.loadDbConfigs();
        return loader;
    }

    @Test
    public void testApiKeysResolvedFromEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        ONTOPORTAL_KEYS.forEach(key -> environment.setProperty(key, "key-" + key));
        environment.setProperty("EARTHPORTAL_API_KEY", " earthportal-key\n");

        ConfigurationLoader loader = load(environment);

        assertThat(loader.getConfigByName("agroportal").getApiKey()).isEqualTo("key-AGROPORTAL_API_KEY");
        assertThat(loader.getConfigByName("earthportal").getApiKey()).isEqualTo("earthportal-key");
    }

    @Test
    public void testMissingOrBlankApiKeyFailsAtStartup() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("AGROPORTAL_API_KEY", "")
                .withProperty("EARTHPORTAL_API_KEY", "key")
                .withProperty("BIODIVPORTAL_API_KEY", "key")
                .withProperty("ECOPORTAL_API_KEY", "  ");

        assertThatThrownBy(() -> load(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[AGROPORTAL_API_KEY, ECOPORTAL_API_KEY, ONTOPORTAL_ASTRO_API_KEY]");
    }

    @Test
    public void testMaskApiKey() {
        assertThat(ApiAccessor.maskApiKey("https://data.agroportal.eu/search?q=plant&apikey=1234-abcd&pagesize=150"))
                .isEqualTo("https://data.agroportal.eu/search?q=plant&apikey=***&pagesize=150");
        assertThat(ApiAccessor.maskApiKey("ontologies/X?apikey=1234-abcd")).isEqualTo("ontologies/X?apikey=***");
        assertThat(ApiAccessor.maskApiKey(null)).isNull();
    }
}
