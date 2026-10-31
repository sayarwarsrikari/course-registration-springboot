package com.example.courseregistration.exception;

/** Raised when a drop is requested for a learner who is not enrolled. The drop must not change state. */
public class EnrollmentNotFoundException extends RuntimeException {

    private final String learnerId;
    private final String courseId;

    public EnrollmentNotFoundException(String learnerId, String courseId) {
        super("Learner '" + learnerId + "' is not enrolled in course '" + courseId + "'.");
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
