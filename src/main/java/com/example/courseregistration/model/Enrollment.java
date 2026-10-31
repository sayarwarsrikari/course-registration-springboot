package com.example.courseregistration.model;

import java.time.Instant;

/** A confirmed seat. Deleting this record is the "drop" operation. */
public record Enrollment(String learnerId, String courseId, Instant enrolledAt) {

    public static Enrollment now(String learnerId, String courseId) {
        return new Enrollment(learnerId, courseId, Instant.now());
    }
}
