package com.example.courseregistration.exception;

/** Raised when a requested course ID does not exist. */
public class CourseNotFoundException extends RuntimeException {

    private final String courseId;

    public CourseNotFoundException(String courseId) {
        super("Course '" + courseId + "' not found.");
        this.courseId = courseId;
    }

    public String getCourseId() {
        return courseId;
    }
}
