package com.example.courseregistration.model;

import java.util.List;

/**
 * A single run of a course with a fixed seat limit.
 *
 * <p>Prerequisites are mutable on purpose: the original test-suite adds a prerequisite to an
 * existing course to simulate a waitlisted learner who becomes ineligible before promotion.
 */
public class CourseOffering {

    private final String id;
    private final String name;
    private final int capacity;
    private volatile List<String> prerequisites;

    public CourseOffering(String id, String name, int capacity, List<String> prerequisites) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public List<String> getPrerequisites() {
        return prerequisites;
    }

    public void setPrerequisites(List<String> prerequisites) {
        this.prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
    }
}
