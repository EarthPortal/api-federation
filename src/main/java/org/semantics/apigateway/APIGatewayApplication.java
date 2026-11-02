package org.semantics.apigateway;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.semantics.apigateway.model.user.Role;
import org.semantics.apigateway.model.user.User;
import org.semantics.apigateway.service.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.concurrent.Executor;

@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync
@EnableCaching
// OpenAPI info (title/description) and tags are defined programmatically in
// org.semantics.apigateway.config.OpenApiConfig so they can be configured per instance.
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class APIGatewayApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(APIGatewayApplication.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${user.admin.password}")
    private String password;

    public APIGatewayApplication(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static void main(String[] args) {

        SpringApplication.run(APIGatewayApplication.class, args);
    }

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }

    @Override
    public void run(String... args) throws Exception {
        this.userRepository.findByUsername("admin").ifPresentOrElse(
                user -> logger.info("Admin user already exists"),
                () -> {
                    logger.info("Creating admin user");
                    User admin = new User();
                    admin.setUsername("admin");
                    admin.setPassword(passwordEncoder.encode(password));
                    admin.setRoles(Collections.singleton(Role.ADMIN));
                    this.userRepository.save(admin);
                    logger.info("Admin user created");
                }
        );
    }
}
