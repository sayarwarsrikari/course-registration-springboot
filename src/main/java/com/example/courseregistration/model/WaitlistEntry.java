package com.example.courseregistration.model;

import java.time.Instant;

/** A learner's place in the FIFO queue for a full course. Ascending {@code joinedAt} = queue order. */
public record WaitlistEntry(String learnerId, String courseId, Instant joinedAt) {

    public static WaitlistEntry now(String learnerId, String courseId) {
        return new WaitlistEntry(learnerId, courseId, Instant.now());
    }
}
