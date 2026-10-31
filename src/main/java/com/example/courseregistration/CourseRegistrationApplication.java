package com.example.courseregistration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Replaces FastAPI's {@code create_app()} factory: Spring's component scan wires one
 * shared {@code CourseRepository} -> {@code EnrollmentService} -> {@code EnrollmentController}
 * chain, so no manual dependency overrides are needed.
 */
@SpringBootApplication
public class CourseRegistrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CourseRegistrationApplication.class, args);
    }
}
