package com.example.courseregistration.exception;

/** Raised when a learner already on the waitlist tries to join again. */
public class AlreadyWaitlistedException extends RuntimeException {

    private final String learnerId;
    private final String courseId;

    public AlreadyWaitlistedException(String learnerId, String courseId) {
        super("Learner '" + learnerId + "' is already on the waitlist for course '" + courseId + "'.");
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
