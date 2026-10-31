package com.example.courseregistration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Payload for an enrollment request. JSON: {learner_id}. */
public record EnrollRequest(
        @NotNull @Schema(description = "ID of the learner requesting enrollment.") String learnerId) {}
