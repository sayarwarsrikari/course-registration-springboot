package com.example.courseregistration.exception;

import java.util.List;
import java.util.stream.Collectors;

/** Raised when a learner has not completed every prerequisite. Carries the missing course IDs. */
public class PrerequisiteNotMetException extends RuntimeException {

    private final String learnerId;
    private final String courseId;
    private final List<String> missing;

    public PrerequisiteNotMetException(String learnerId, String courseId, List<String> missing) {
        super("Learner '" + learnerId + "' is missing prerequisites " + pythonStyle(missing)
                + " for course '" + courseId + "'.");
        this.learnerId = learnerId;
        this.courseId = courseId;
        this.missing = List.copyOf(missing);
    }

    /** Renders ['a', 'b'] so messages stay identical to the FastAPI version. */
    private static String pythonStyle(List<String> items) {
        return items.stream().map(s -> "'" + s + "'").collect(Collectors.joining(", ", "[", "]"));
    }

    public String getLearnerId() {
        return learnerId;
    }

    public String getCourseId() {
        return courseId;
    }

    public List<String> getMissing() {
        return missing;
    }
}
