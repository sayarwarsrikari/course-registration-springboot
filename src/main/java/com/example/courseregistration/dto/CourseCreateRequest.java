package com.example.courseregistration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Payload to create a course offering. JSON: {id, name, capacity, prerequisites}. */
public record CourseCreateRequest(
        @NotNull @Schema(description = "Unique course identifier, e.g. 'python-201'.") String id,
        @NotNull @Schema(description = "Human-readable course name.") String name,
        @NotNull @Positive @Schema(description = "Maximum number of enrolled learners. Must be positive.")
                Integer capacity,
        @Schema(description = "Course IDs that must be completed before enrollment.")
                List<String> prerequisites) {

    public CourseCreateRequest {
        prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }
}
