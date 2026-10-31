package com.example.courseregistration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response after an enroll attempt. {@code status} is "enrolled" or "waitlisted"; {@code position}
 * is the 1-indexed queue position, or null when enrolled.
 */
public record EnrollResponse(
        String learnerId,
        String courseId,
        String status,
        @Schema(description = "Waitlist position (1-indexed). Null if status is 'enrolled'.") Integer position) {}
