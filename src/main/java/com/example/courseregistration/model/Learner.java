package com.example.courseregistration.model;

import java.util.List;

/**
 * A student. {@code completedCourses} holds the IDs of courses already finished; the prerequisite
 * check reads from it.
 */
public record Learner(String id, String name, List<String> completedCourses) {

    public Learner {
        completedCourses = completedCourses == null ? List.of() : List.copyOf(completedCourses);
    }
}
