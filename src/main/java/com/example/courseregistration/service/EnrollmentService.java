package com.example.courseregistration.service;

import com.example.courseregistration.exception.AlreadyEnrolledException;
import com.example.courseregistration.exception.AlreadyWaitlistedException;
import com.example.courseregistration.exception.CourseNotFoundException;
import com.example.courseregistration.exception.EnrollmentNotFoundException;
import com.example.courseregistration.exception.LearnerNotFoundException;
import com.example.courseregistration.exception.PrerequisiteNotMetException;
import com.example.courseregistration.model.CourseOffering;
import com.example.courseregistration.model.Learner;
import com.example.courseregistration.model.WaitlistEntry;
import com.example.courseregistration.repository.CourseRepository;
import com.example.courseregistration.repository.CourseRepository.Action;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

/**
 * All business logic lives here. The service validates rules, orchestrates the repository and
 * throws domain exceptions; it knows nothing about HTTP. It can be unit-tested with
 * {@code new EnrollmentService(new CourseRepository())}.
 */
@Service
public class EnrollmentService {

    // ----- result types (plain values returned to the controller) -----

    /** Outcome of an enroll attempt. {@code position} is null when enrolled (1-indexed otherwise). */
    public record EnrollResult(String status, Integer position) {
        public static final String ENROLLED = "enrolled";
        public static final String WAITLISTED = "waitlisted";

        static EnrollResult enrolled() {
            return new EnrollResult(ENROLLED, null);
        }

        static EnrollResult waitlisted(int position) {
            return new EnrollResult(WAITLISTED, position);
        }
    }

    /** Outcome of a drop. {@code promotedLearnerId} is null if nobody was promoted. */
    public record DropResult(String learnerId, String courseId, String promotedLearnerId) {}

    public record WaitlistPosition(String learnerId, int position) {}

    /** Snapshot of a course's enrollment and waitlist. */
    public record CourseStatus(
            String courseId,
            String courseName,
            int capacity,
            int enrolledCount,
            int availableSeats,
            List<String> enrolledLearnerIds,
            List<WaitlistPosition> waitlist) {}

    // -----------------------------------------------------------------------

    private final CourseRepository repo;

    public EnrollmentService(CourseRepository repository) {
        this.repo = repository;
    }

    // ----- administrative operations -----

    public CourseOffering registerCourse(String id, String name, int capacity, List<String> prerequisites) {
        CourseOffering course = new CourseOffering(id, name, capacity, prerequisites);
        repo.addCourse(course);
        return course;
    }

    public Learner registerLearner(String id, String name, List<String> completedCourses) {
        Learner learner = new Learner(id, name, completedCourses);
        repo.addLearner(learner);
        return learner;
    }

    // ----- enrollment -----

    /**
     * Decision tree:
     * <ol>
     *   <li>course exists? else {@link CourseNotFoundException}
     *   <li>learner exists? else {@link LearnerNotFoundException}
     *   <li>already enrolled? {@link AlreadyEnrolledException}
     *   <li>already waitlisted? {@link AlreadyWaitlistedException}
     *   <li>prerequisites met? else {@link PrerequisiteNotMetException}
     *   <li>seat free -> enrolled; otherwise -> waitlisted (FIFO) with 1-indexed position
     * </ol>
     */
    public EnrollResult enroll(String courseId, String learnerId) {
        CourseOffering course = getCourseOrThrow(courseId);
        Learner learner = getLearnerOrThrow(learnerId);

        if (repo.getEnrollment(learnerId, courseId).isPresent()) {
            throw new AlreadyEnrolledException(learnerId, courseId);
        }
        if (repo.getWaitlistEntry(learnerId, courseId).isPresent()) {
            throw new AlreadyWaitlistedException(learnerId, courseId);
        }

        validatePrerequisites(learner, course);

        boolean seatAvailable = repo.countEnrollments(courseId) < course.getCapacity();

        if (seatAvailable) {
            // Another request may grab the last seat first; the repository re-checks under its lock
            // and waitlists us instead if so.
            repo.atomicEnrollOrWaitlist(course, learnerId, Action.ENROLL);
            if (repo.getEnrollment(learnerId, courseId).isPresent()) {
                return EnrollResult.enrolled();
            }
            return EnrollResult.waitlisted(findPosition(repo.getWaitlistForCourse(courseId), learnerId));
        }

        // Course is full -> straight to the waitlist
        repo.atomicEnrollOrWaitlist(course, learnerId, Action.WAITLIST);
        return EnrollResult.waitlisted(findPosition(repo.getWaitlistForCourse(courseId), learnerId));
    }

    // ----- drop + FIFO promotion -----

    /**
     * Drops an enrolled learner and promotes the first waitlisted learner who still satisfies the
     * prerequisites. Ineligible learners are skipped but keep their queue position.
     */
    public DropResult drop(String courseId, String learnerId) {
        CourseOffering course = getCourseOrThrow(courseId);
        getLearnerOrThrow(learnerId);

        if (repo.getEnrollment(learnerId, courseId).isEmpty()) {
            throw new EnrollmentNotFoundException(learnerId, courseId);
        }

        // Prerequisites are re-checked at promotion time (outside the lock: learners are immutable).
        List<WaitlistEntry> eligible = filterEligible(repo.getWaitlistForCourse(courseId), course);

        String promoted = repo.atomicDropAndPromote(learnerId, course, eligible).orElse(null);
        return new DropResult(learnerId, courseId, promoted);
    }

    // ----- status -----

    public CourseStatus getCourseStatus(String courseId) {
        CourseOffering course = getCourseOrThrow(courseId);

        List<String> enrolledIds = repo.getEnrolledLearnerIds(courseId);
        int enrolledCount = enrolledIds.size();

        List<WaitlistEntry> entries = repo.getWaitlistForCourse(courseId);
        List<WaitlistPosition> waitlist = IntStream.range(0, entries.size())
                .mapToObj(i -> new WaitlistPosition(entries.get(i).learnerId(), i + 1))
                .toList();

        return new CourseStatus(
                course.getId(),
                course.getName(),
                course.getCapacity(),
                enrolledCount,
                course.getCapacity() - enrolledCount,
                enrolledIds,
                waitlist);
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private CourseOffering getCourseOrThrow(String courseId) {
        return repo.getCourse(courseId).orElseThrow(() -> new CourseNotFoundException(courseId));
    }

    private Learner getLearnerOrThrow(String learnerId) {
        return repo.getLearner(learnerId).orElseThrow(() -> new LearnerNotFoundException(learnerId));
    }

    /** missing = required - completed; throws if non-empty (sorted for deterministic messages). */
    private void validatePrerequisites(Learner learner, CourseOffering course) {
        Set<String> missing = new TreeSet<>(course.getPrerequisites());
        missing.removeAll(new HashSet<>(learner.completedCourses()));
        if (!missing.isEmpty()) {
            throw new PrerequisiteNotMetException(learner.id(), course.getId(), List.copyOf(missing));
        }
    }

    /** Keeps FIFO order; drops entries whose learner lacks prerequisites (they stay on the real waitlist). */
    private List<WaitlistEntry> filterEligible(List<WaitlistEntry> waitlist, CourseOffering course) {
        return waitlist.stream()
                .filter(entry -> repo.getLearner(entry.learnerId())
                        .map(learner -> new HashSet<>(learner.completedCourses()).containsAll(course.getPrerequisites()))
                        .orElse(false)) // learner vanished after joining -> skip
                .toList();
    }

    /** 1-indexed position in the waitlist. Missing learner indicates a bug, not user error. */
    private static int findPosition(List<WaitlistEntry> waitlist, String learnerId) {
        for (int i = 0; i < waitlist.size(); i++) {
            if (waitlist.get(i).learnerId().equals(learnerId)) {
                return i + 1;
            }
        }
        throw new IllegalStateException("Learner '" + learnerId + "' not found in waitlist - this is a bug.");
    }
}
