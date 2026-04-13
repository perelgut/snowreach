package com.yosnowmow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * YoSnowMow API — Spring Boot application entry point.
 *
 * {@code @EnableAsync} activates Spring's asynchronous method execution for
 * {@code @Async}-annotated methods (used by MatchingService in P1-09 so that
 * Worker matching does not block the POST /api/jobs HTTP response).
 */
@SpringBootApplication
@EnableAsync
public class YoSnowMowApplication {
    public static void main(String[] args) {
        SpringApplication.run(YoSnowMowApplication.class, args);
    }
}
