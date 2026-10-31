package com.example.courseregistration.exception;

/** Raised when an already-enrolled learner tries to enroll again. No state changes. */
public class AlreadyEnrolledException extends RuntimeException {

    private final String learnerId;
    private final String courseId;

    public AlreadyEnrolledException(String learnerId, String courseId) {
        super("Learner '" + learnerId + "' is already enrolled in course '" + courseId + "'.");
        this.learnerId = learnerId;
        this.courseId = courseId;
    }

    public String getLearnerId() {
        return learnerId;
    }

    public String getCourseId() {
        return courseId;
    }
}
