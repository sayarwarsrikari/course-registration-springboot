package com.example.courseregistration.dto;

import java.util.List;

/** Full snapshot of a course's enrollment and waitlist state. */
public record CourseStatusResponse(
        String courseId,
        String courseName,
        int capacity,
        int enrolledCount,
        int availableSeats,
        List<String> enrolledLearnerIds,
        List<WaitlistEntryResponse> waitlist) {}
