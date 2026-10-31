package com.example.courseregistration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Payload to register a new learner. JSON: {id, name, completed_courses}. */
public record LearnerCreateRequest(
        @NotNull @Schema(description = "Unique identifier for this learner.") String id,
        @NotNull @Schema(description = "Full name of the learner.") String name,
        @Schema(description = "Course IDs the learner has already completed.") List<String> completedCourses) {

    public LearnerCreateRequest {
        completedCourses = completedCourses == null ? List.of() : List.copyOf(completedCourses);
    }
}
