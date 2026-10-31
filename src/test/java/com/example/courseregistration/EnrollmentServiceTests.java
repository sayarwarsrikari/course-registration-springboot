package com.example.courseregistration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.courseregistration.model.WaitlistEntry;
import com.example.courseregistration.repository.CourseRepository;
import com.example.courseregistration.service.EnrollmentService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Service-level tests: no Spring context, no HTTP. */
class EnrollmentServiceTests {

    @Test
    void serviceSkipsIneligibleLearnerAndPromotesNextEligible() {
        CourseRepository repo = new CourseRepository();
        EnrollmentService svc = new EnrollmentService(repo);

        // Course has no prereqs initially, which lets L2 join the waitlist.
        svc.registerCourse("C1", "Course 1", 1, List.of());
        svc.registerLearner("L1", "Learner 1", List.of("python-101"));
        svc.registerLearner("L2", "Learner 2", List.of()); // will become ineligible
        svc.registerLearner("L3", "Learner 3", List.of("python-101"));

        svc.enroll("C1", "L1");
        svc.enroll("C1", "L2"); // waitlist position 1
        svc.enroll("C1", "L3"); // waitlist position 2

        // Retroactively require a prerequisite.
        repo.getCourse("C1").orElseThrow().setPrerequisites(List.of("python-101"));

        EnrollmentService.DropResult result = svc.drop("C1", "L1");

        assertThat(result.promotedLearnerId()).isEqualTo("L3");

        List<String> waitlistIds =
                repo.getWaitlistForCourse("C1").stream().map(WaitlistEntry::learnerId).toList();
        assertThat(waitlistIds).contains("L2"); // skipped, but keeps its place
        assertThat(waitlistIds).doesNotContain("L3"); // promoted
    }

    /** Proves the "enrolled count never exceeds capacity" invariant holds under real concurrency. */
    @Test
    void concurrentEnrollmentsNeverExceedCapacity() throws Exception {
        int capacity = 5;
        int learners = 60;
        CourseRepository repo = new CourseRepository();
        EnrollmentService svc = new EnrollmentService(repo);
        svc.registerCourse("C1", "Busy course", capacity, List.of());
        for (int i = 0; i < learners; i++) {
            svc.registerLearner("L" + i, "Learner " + i, List.of());
        }

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < learners; i++) {
            String learnerId = "L" + i;
            futures.add(pool.submit(() -> {
                startGun.await();
                svc.enroll("C1", learnerId);
                return null;
            }));
        }
        startGun.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        EnrollmentService.CourseStatus status = svc.getCourseStatus("C1");
        assertThat(status.enrolledCount()).isEqualTo(capacity);
        assertThat(status.availableSeats()).isZero();
        assertThat(status.waitlist()).hasSize(learners - capacity);
    }
}
