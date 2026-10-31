package com.example.courseregistration.repository;

import com.example.courseregistration.model.CourseOffering;
import com.example.courseregistration.model.Enrollment;
import com.example.courseregistration.model.Learner;
import com.example.courseregistration.model.WaitlistEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * In-memory repository for courses, learners, enrollments and waitlist entries.
 *
 * <p>This is the ONLY class that touches the underlying collections. Swapping to a database later
 * means replacing this class (e.g. with Spring Data JPA + {@code SELECT ... FOR UPDATE}); the
 * service layer would not change.
 *
 * <p>Thread-safety: Spring MVC handles each request on its own thread, so every method (reads
 * included, because {@link ArrayList}/{@link HashMap} are not safe for concurrent
 * read-while-write) is {@code synchronized} on this instance. Intrinsic locks are reentrant, so the
 * atomic operations below can freely call the simple methods. The capacity check and the insert in
 * {@link #atomicEnrollOrWaitlist} happen under ONE lock acquisition, which prevents the classic
 * check-then-act (TOCTOU) over-enrollment race.
 */
@Repository
public class CourseRepository {

    /** What {@link #atomicEnrollOrWaitlist} should try to do. */
    public enum Action {
        ENROLL,
        WAITLIST
    }

    private record EnrollmentKey(String learnerId, String courseId) {}

    private final Map<String, CourseOffering> courses = new HashMap<>();
    private final Map<String, Learner> learners = new HashMap<>();
    private final Map<EnrollmentKey, Enrollment> enrollments = new HashMap<>();
    // Append-only; FIFO order is also enforced by sorting on joinedAt (stable) when read.
    private final List<WaitlistEntry> waitlist = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Courses
    // -----------------------------------------------------------------------

    /** Persists a course. Silently overwrites if the same ID is added again. */
    public synchronized void addCourse(CourseOffering course) {
        courses.put(course.getId(), course);
    }

    public synchronized Optional<CourseOffering> getCourse(String courseId) {
        return Optional.ofNullable(courses.get(courseId));
    }

    // -----------------------------------------------------------------------
    // Learners
    // -----------------------------------------------------------------------

    public synchronized void addLearner(Learner learner) {
        learners.put(learner.id(), learner);
    }

    public synchronized Optional<Learner> getLearner(String learnerId) {
        return Optional.ofNullable(learners.get(learnerId));
    }

    // -----------------------------------------------------------------------
    // Enrollments
    // -----------------------------------------------------------------------

    public synchronized Optional<Enrollment> getEnrollment(String learnerId, String courseId) {
        return Optional.ofNullable(enrollments.get(new EnrollmentKey(learnerId, courseId)));
    }

    public synchronized int countEnrollments(String courseId) {
        return (int) enrollments.keySet().stream().filter(k -> k.courseId().equals(courseId)).count();
    }

    /** Learner IDs enrolled in the course, sorted for deterministic output. */
    public synchronized List<String> getEnrolledLearnerIds(String courseId) {
        return enrollments.keySet().stream()
                .filter(k -> k.courseId().equals(courseId))
                .map(EnrollmentKey::learnerId)
                .sorted()
                .toList();
    }

    public synchronized void addEnrollment(Enrollment enrollment) {
        enrollments.put(new EnrollmentKey(enrollment.learnerId(), enrollment.courseId()), enrollment);
    }

    /** @return true if an enrollment was removed, false if there was none. */
    public synchronized boolean removeEnrollment(String learnerId, String courseId) {
        return enrollments.remove(new EnrollmentKey(learnerId, courseId)) != null;
    }

    // -----------------------------------------------------------------------
    // Waitlist
    // -----------------------------------------------------------------------

    public synchronized Optional<WaitlistEntry> getWaitlistEntry(String learnerId, String courseId) {
        return waitlist.stream()
                .filter(e -> e.learnerId().equals(learnerId) && e.courseId().equals(courseId))
                .findFirst();
    }

    /** Waitlist for one course, ordered by joinedAt ascending. Index 0 = next in line. */
    public synchronized List<WaitlistEntry> getWaitlistForCourse(String courseId) {
        // List.sort / Stream.sorted are stable, so equal timestamps keep insertion order.
        return waitlist.stream()
                .filter(e -> e.courseId().equals(courseId))
                .sorted(Comparator.comparing(WaitlistEntry::joinedAt))
                .toList();
    }

    public synchronized void addToWaitlist(WaitlistEntry entry) {
        waitlist.add(entry);
    }

    /** @return true if an entry was found and removed. */
    public synchronized boolean removeFromWaitlist(String learnerId, String courseId) {
        return waitlist.removeIf(e -> e.learnerId().equals(learnerId) && e.courseId().equals(courseId));
    }

    // -----------------------------------------------------------------------
    // Atomic operations
    // -----------------------------------------------------------------------

    /**
     * Performs the capacity check and the insert under a single lock acquisition.
     *
     * <p>If {@code action == ENROLL} but the course filled up in the meantime (another request took
     * the last seat between the service's pre-check and this call), the learner is waitlisted
     * instead, so enrolled count can never exceed capacity.
     */
    public synchronized void atomicEnrollOrWaitlist(CourseOffering course, String learnerId, Action action) {
        if (action == Action.ENROLL) {
            if (countEnrollments(course.getId()) >= course.getCapacity()) {
                addToWaitlist(WaitlistEntry.now(learnerId, course.getId()));
                return;
            }
            addEnrollment(Enrollment.now(learnerId, course.getId()));
        } else {
            addToWaitlist(WaitlistEntry.now(learnerId, course.getId()));
        }
    }

    /**
     * Drops the learner and promotes the first eligible waitlist entry, all under one lock.
     *
     * @param eligibleWaitlist pre-filtered (prerequisites satisfied), FIFO-ordered candidates built
     *     by the service. Ineligible learners are simply absent from this list, so they are skipped
     *     but remain in the real waitlist at their original position.
     * @return the promoted learner's ID, or empty if nobody was promoted
     */
    public synchronized Optional<String> atomicDropAndPromote(
            String learnerId, CourseOffering course, List<WaitlistEntry> eligibleWaitlist) {
        if (!removeEnrollment(learnerId, course.getId())) {
            return Optional.empty(); // defensive: service validates first
        }
        if (eligibleWaitlist.isEmpty()) {
            return Optional.empty();
        }
        WaitlistEntry promoted = eligibleWaitlist.get(0);
        removeFromWaitlist(promoted.learnerId(), course.getId());
        addEnrollment(Enrollment.now(promoted.learnerId(), course.getId()));
        return Optional.of(promoted.learnerId());
    }

    /** Resets all state. For tests only. */
    public synchronized void clearAll() {
        courses.clear();
        learners.clear();
        enrollments.clear();
        waitlist.clear();
    }
}
