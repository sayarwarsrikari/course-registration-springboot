package com.example.courseregistration.exception;

/** Raised when a requested learner ID does not exist. */
public class LearnerNotFoundException extends RuntimeException {

    private final String learnerId;

    public LearnerNotFoundException(String learnerId) {
        super("Learner '" + learnerId + "' not found.");
        this.learnerId = learnerId;
    }

    public String getLearnerId() {
        return learnerId;
    }
}
