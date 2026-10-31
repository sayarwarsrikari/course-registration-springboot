package com.example.courseregistration.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** App-level metadata shown in Swagger UI (was {@code core/config.py}). */
@Configuration
public class OpenApiConfig {

    public static final String APP_TITLE = "Course Registration Service";
    public static final String APP_VERSION = "1.0.0";
    public static final String APP_DESCRIPTION =
            "A bounded course-registration API with FIFO waitlist management, "
                    + "prerequisite validation, and automatic seat promotion.";

    @Bean
    public OpenAPI courseRegistrationOpenApi() {
        return new OpenAPI().info(new Info()
                .title(APP_TITLE)
                .version(APP_VERSION)
                .description(APP_DESCRIPTION));
    }
}
