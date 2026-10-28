package org.semantics.apigateway.service;

import lombok.AllArgsConstructor;
import lombok.Setter;
import org.semantics.apigateway.model.responses.ApiResponse;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Setter
@AllArgsConstructor
@Service
public class ApiAccessor {

    private RestTemplate restTemplate;
    private Map<String, UrlConfig> urls;
    private Logger logger;
    private boolean unDecodeUrl;
    private CacheService cacheService;
    private boolean cacheEnabled;
    private PortalHealthRegistry portalHealth;

    @Autowired
    public ApiAccessor(CacheManager cacheManager, PortalHealthRegistry portalHealth) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        this.cacheService = new CacheService(cacheManager);
        this.portalHealth = portalHealth;
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
        this.urls = new HashMap<>();
        this.unDecodeUrl = false;
        this.cacheEnabled = true;
    }

    public Map<String, UrlConfig> getUrls() {
        return urls;
    }

    private Map<String, UrlConfig> reachableUrls() {
        if (portalHealth == null) return this.urls;
        Map<String, UrlConfig> reachable = new HashMap<>();
        this.urls.forEach((url, cfg) -> {
            String base = PortalHealthRegistry.baseUrl(url);
            if (portalHealth.isUnreachable(base)) {
                logger.info("Skipping portal in unreachable window: {}", base);
            } else {
                reachable.put(url, cfg);
            }
        });
        return reachable.isEmpty() ? this.urls : reachable;
    }

    @Async
    public CompletableFuture<Map<String, ApiResponse>> get() {
        return get("");
    }

    @Async
    public CompletableFuture<Map<String, ApiResponse>> get(String... query) {
        Map<String, UrlConfig> targets = reachableUrls();
        ForkJoinPool customThreadPool = new ForkJoinPool(Math.max(targets.size(), 1));

        List<CompletableFuture<Map.Entry<String, ApiResponse>>> futures = targets.entrySet().stream()
                .map(config -> CompletableFuture.supplyAsync(() -> call(config.getKey(), config.getValue(), query), customThreadPool)
                        .thenApply(response -> Map.entry(config.getKey(), response))
                )
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .collect(Collectors.toMap(
                                future -> future.join().getKey(),
                                future -> future.join().getValue()
                        ))
                )
                .exceptionally(e -> {
                    logger.error("Error processing results: {}", e.getMessage(), e);
                    return Collections.emptyMap();
                });
    }


    public ApiResponse call(String url, UrlConfig urlConfig, String... query) {
        ApiResponse result = new ApiResponse();
        String fullUrl = url;
        result.setUrl(url);
        try {
            fullUrl = constructUrl(url, urlConfig, query);

            if (cacheService.exists(fullUrl) && cacheEnabled) {
                logger.info("Cached result for request URL: {} and query parameters: {}", url, query);
                return (ApiResponse) cacheService.read(fullUrl);
            }

            if (!cacheEnabled)
                logger.info("Cache is disabled");

            if (url.endsWith("/localData")) {
                logger.info("Local data requested for URL: {}", fullUrl);
                Map<String, Object> out = new HashMap<>();
                List<Object> collection = new ArrayList<>();
                collection.add(new HashMap<>());
                out.put("collection", collection);
                result.setResponseBody(out);
                return result;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.ALL));
            if (url.startsWith("https://test.iconclass.org/")) {
                headers.setBearerAuth("foobarbax");
            }
            HttpEntity<String> entity = new HttpEntity<>("body", headers);
            
            logger.info("Accessing URL: {}", fullUrl);
            
            long startTime = System.currentTimeMillis();

            ResponseEntity<?> response;
            URL uri = new URL(fullUrl);
            if (unDecodeUrl) {
                restTemplate.setInterceptors(Collections.singletonList(new UriDecodingInterceptor()));
            }
            
            response = restTemplate.exchange(uri.toString(), HttpMethod.GET, entity, Object.class);

            long endTime = System.currentTimeMillis();
            long responseTime = endTime - startTime;
            logger.info("URL accessed {} in {}s", fullUrl, responseTime);
            result.setResponseTime(responseTime);

            result.setStatusCode(response.getStatusCodeValue());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.debug("Raw API Response: {}", response.getBody());
                if (response.getBody() instanceof List) {
                    Map<String, Object> out = new HashMap<>();
                    out.put("collection", response.getBody());
                    result.setResponseBody(out);
                } else {
                    result.setResponseBody((Map<String, Object>) response.getBody());
                }
                logger.info("Write cache for request URL: {} and query parameters: {}", url, query);
                cacheService.write(fullUrl, result);
                markReachable(url);
                return result;
            } else {
                logger.error("API {} Response Error: Status Code - {}", fullUrl, response.getStatusCode());
                return result;
            }
        } catch (Exception e) {
            logger.error("An error occurred while processing the request {}: {}", fullUrl, e.getMessage());
            if (e instanceof ResourceAccessException) {
                markUnreachable(url);
            }
            return result;
        }
    }

    private String constructUrl(String url, UrlConfig config, String... query) {
        String apikey = config.apikey();
        boolean isCaseInSensitive = config.caseInSensitive();
        List<String> queries = new ArrayList<>(List.of(query));

        if(isCaseInSensitive)
            queries.set(0, queries.get(0).toUpperCase());

        queries = queries.stream().filter(x -> !x.isEmpty()).collect(Collectors.toList());
        
        // TODO It is assumed that the last query argument is the pagination offset. This should be handled in a safer way.
        
        if (!queries.isEmpty()) {
            try {
                Integer page = Integer.parseInt(queries.get(queries.size() - 1)) - 1 + config.pagination().getFirst();
                queries = Stream.concat(queries.stream().limit(queries.size() - 1), Stream.of(page.toString())).collect(Collectors.toList());
            } catch (NumberFormatException e) {
                logger.info("Pagination parameter missing for URL {} with query: {}", url, queries);
            }
        }
        
        if (!apikey.isEmpty()) {
            queries.add(apikey);
        }

        if (queries.isEmpty()) {
            return url;
        } else {
            return String.format(url, queries.toArray());
        }

    }

    private void markReachable(String url) {
        if (portalHealth != null) portalHealth.markReachable(PortalHealthRegistry.baseUrl(url));
    }

    private void markUnreachable(String url) {
        if (portalHealth != null) portalHealth.markUnreachable(PortalHealthRegistry.baseUrl(url));
    }
}
