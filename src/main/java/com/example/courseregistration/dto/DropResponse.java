package com.example.courseregistration.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response after a successful drop. */
public record DropResponse(
        String learnerId,
        String courseId,
        String status,
        @Schema(description = "Learner promoted from the waitlist after this drop. Null if no promotion.")
                String promotedLearnerId) {

    public static DropResponse dropped(String learnerId, String courseId, String promotedLearnerId) {
        return new DropResponse(learnerId, courseId, "dropped", promotedLearnerId);
    }
}
