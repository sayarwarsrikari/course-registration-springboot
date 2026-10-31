package com.example.courseregistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.courseregistration.repository.CourseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * HTTP-level test suite (ported from tests/test_enrollment.py). Each test starts from an empty
 * repository and creates its own courses/learners.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnrollmentApiTests {

    @Autowired MockMvc mvc;
    @Autowired CourseRepository repository;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void resetState() {
        repository.clearAll(); // test isolation: the repository is a shared singleton bean
    }

    // ---------------------------------------------------------------- helpers

    private void createCourse(String id, int capacity, String... prerequisites) throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "id", id,
                "name", "Course " + id,
                "capacity", capacity,
                "prerequisites", List.of(prerequisites)));
        mvc.perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private void createLearner(String id, String... completed) throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "id", id,
                "name", "Learner " + id,
                "completed_courses", List.of(completed)));
        mvc.perform(post("/api/v1/learners").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private MvcResult enroll(String courseId, String learnerId) throws Exception {
        return mvc.perform(post("/api/v1/courses/" + courseId + "/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("learner_id", learnerId))))
                .andReturn();
    }

    private MvcResult drop(String courseId, String learnerId) throws Exception {
        return mvc.perform(delete("/api/v1/courses/" + courseId + "/enrollments/" + learnerId)).andReturn();
    }

    private JsonNode courseStatus(String courseId) throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/courses/" + courseId + "/status"))
                .andExpect(status().isOk())
                .andReturn();
        return body(r);
    }

    private JsonNode body(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    private static int code(MvcResult r) {
        return r.getResponse().getStatus();
    }

    // ================================================== 1. successful enrollment

    @Nested
    class EnrollmentWithAvailableSeats {

        @Test
        void eligibleLearnerGetsEnrolledWhenSeatIsAvailable() throws Exception {
            createCourse("C1", 2);
            createLearner("L1");

            MvcResult resp = enroll("C1", "L1");

            assertThat(code(resp)).isEqualTo(200);
            JsonNode b = body(resp);
            assertThat(b.get("status").asText()).isEqualTo("enrolled");
            assertThat(b.get("position").isNull()).isTrue();
            assertThat(b.get("learner_id").asText()).isEqualTo("L1");
            assertThat(b.get("course_id").asText()).isEqualTo("C1");
        }

        @Test
        void courseStatusReflectsEnrollment() throws Exception {
            createCourse("C1", 3);
            createLearner("L1");
            enroll("C1", "L1");

            JsonNode s = courseStatus("C1");

            assertThat(s.get("enrolled_count").asInt()).isEqualTo(1);
            assertThat(s.get("available_seats").asInt()).isEqualTo(2);
            assertThat(s.get("enrolled_learner_ids").toString()).contains("L1");
            assertThat(s.get("waitlist")).isEmpty();
        }

        @Test
        void multipleLearnersCanFillAllSeats() throws Exception {
            createCourse("C1", 3);
            for (String lid : List.of("L1", "L2", "L3")) {
                createLearner(lid);
                assertThat(body(enroll("C1", lid)).get("status").asText()).isEqualTo("enrolled");
            }

            JsonNode s = courseStatus("C1");
            assertThat(s.get("enrolled_count").asInt()).isEqualTo(3);
            assertThat(s.get("available_seats").asInt()).isEqualTo(0);
        }
    }

    // ================================================== 2. waitlist behaviour

    @Nested
    class WaitlistBehavior {

        @Test
        void learnerIsWaitlistedWhenCourseIsFull() throws Exception {
            createCourse("C1", 1);
            createLearner("L1");
            createLearner("L2");

            enroll("C1", "L1");
            MvcResult resp = enroll("C1", "L2");

            assertThat(code(resp)).isEqualTo(200);
            JsonNode b = body(resp);
            assertThat(b.get("status").asText()).isEqualTo("waitlisted");
            assertThat(b.get("position").asInt()).isEqualTo(1);
        }

        @Test
        void waitlistPositionsAreAssignedInFifoOrder() throws Exception {
            createCourse("C1", 1);
            for (String lid : List.of("L1", "L2", "L3", "L4")) {
                createLearner(lid);
            }
            enroll("C1", "L1");
            enroll("C1", "L2");
            enroll("C1", "L3");
            enroll("C1", "L4");

            JsonNode waitlist = courseStatus("C1").get("waitlist");

            assertThat(waitlist).hasSize(3);
            assertThat(waitlist.get(0).get("learner_id").asText()).isEqualTo("L2");
            assertThat(waitlist.get(0).get("position").asInt()).isEqualTo(1);
            assertThat(waitlist.get(1).get("learner_id").asText()).isEqualTo("L3");
            assertThat(waitlist.get(1).get("position").asInt()).isEqualTo(2);
            assertThat(waitlist.get(2).get("learner_id").asText()).isEqualTo("L4");
            assertThat(waitlist.get(2).get("position").asInt()).isEqualTo(3);
        }
    }

    // ================================================== 3. duplicate requests

    @Nested
    class DuplicateRequests {

        @Test
        void duplicateEnrollmentReturns409() throws Exception {
            createCourse("C1", 3);
            createLearner("L1");

            enroll("C1", "L1");
            MvcResult resp = enroll("C1", "L1");

            assertThat(code(resp)).isEqualTo(409);
            assertThat(body(resp).get("detail").asText().toLowerCase()).contains("already enrolled");
        }

        @Test
        void duplicateEnrollmentDoesNotChangeEnrolledCount() throws Exception {
            createCourse("C1", 2);
            createLearner("L1");

            enroll("C1", "L1");
            enroll("C1", "L1"); // rejected

            assertThat(courseStatus("C1").get("enrolled_count").asInt()).isEqualTo(1);
        }

        @Test
        void duplicateWaitlistReturns409() throws Exception {
            createCourse("C1", 1);
            createLearner("L1");
            createLearner("L2");

            enroll("C1", "L1");
            enroll("C1", "L2"); // waitlisted
            MvcResult resp = enroll("C1", "L2"); // duplicate

            assertThat(code(resp)).isEqualTo(409);
            assertThat(body(resp).get("detail").asText().toLowerCase()).contains("already on the waitlist");
        }

        @Test
        void duplicateWaitlistDoesNotAddExtraEntry() throws Exception {
            createCourse("C1", 1);
            createLearner("L1");
            createLearner("L2");

            enroll("C1", "L1");
            enroll("C1", "L2");
            enroll("C1", "L2"); // rejected

            assertThat(courseStatus("C1").get("waitlist")).hasSize(1);
        }
    }

    // ================================================== 4. drop and FIFO promotion

    @Nested
    class DropAndPromotion {

        @Test
        void dropEnrolledLearnerSucceeds() throws Exception {
            createCourse("C1", 2);
            createLearner("L1");

            enroll("C1", "L1");
            MvcResult resp = drop("C1", "L1");

            assertThat(code(resp)).isEqualTo(200);
            JsonNode b = body(resp);
            assertThat(b.get("status").asText()).isEqualTo("dropped");
            assertThat(b.get("promoted_learner_id").isNull()).isTrue();
        }

        @Test
        void dropFreesTheSeat() throws Exception {
            createCourse("C1", 1);
            createLearner("L1");

            enroll("C1", "L1");
            drop("C1", "L1");

            JsonNode s = courseStatus("C1");
            assertThat(s.get("available_seats").asInt()).isEqualTo(1);
            assertThat(s.get("enrolled_count").asInt()).isEqualTo(0);
        }

        @Test
        void fifoPromotionPromotesFirstEligibleWaitlistedLearner() throws Exception {
            createCourse("C1", 1);
            for (String lid : List.of("L1", "L2", "L3")) {
                createLearner(lid);
            }
            enroll("C1", "L1");
            enroll("C1", "L2");
            enroll("C1", "L3");

            MvcResult resp = drop("C1", "L1");

            assertThat(code(resp)).isEqualTo(200);
            assertThat(body(resp).get("promoted_learner_id").asText()).isEqualTo("L2");

            JsonNode s = courseStatus("C1");
            assertThat(s.get("enrolled_learner_ids").toString()).contains("L2");
            assertThat(s.get("enrolled_count").asInt()).isEqualTo(1);
            assertThat(s.get("waitlist").get(0).get("learner_id").asText()).isEqualTo("L3");
            assertThat(s.get("waitlist").get(0).get("position").asInt()).isEqualTo(1);
        }

        @Test
        void noPromotionWhenWaitlistIsEmpty() throws Exception {
            createCourse("C1", 2);
            createLearner("L1");
            createLearner("L2");

            enroll("C1", "L1");
            enroll("C1", "L2");
            MvcResult resp = drop("C1", "L1");

            assertThat(body(resp).get("promoted_learner_id").isNull()).isTrue();
            assertThat(courseStatus("C1").get("available_seats").asInt()).isEqualTo(1);
        }
    }

    // ================================================== 5. prerequisite validation

    @Nested
    class PrerequisiteValidation {

        @Test
        void learnerWithoutPrereqsCannotEnroll() throws Exception {
            createCourse("C1", 3, "python-101");
            createLearner("L1");

            MvcResult resp = enroll("C1", "L1");

            assertThat(code(resp)).isEqualTo(422);
            JsonNode detail = body(resp).get("detail");
            assertThat(detail.get("missing_prerequisites").toString()).contains("python-101");
        }

        @Test
        void learnerWithAllPrereqsCanEnroll() throws Exception {
            createCourse("C1", 3, "python-101");
            createLearner("L1", "python-101");

            MvcResult resp = enroll("C1", "L1");

            assertThat(code(resp)).isEqualTo(200);
            assertThat(body(resp).get("status").asText()).isEqualTo("enrolled");
        }

        @Test
        void learnerWithoutPrereqsCannotJoinWaitlist() throws Exception {
            createCourse("C1", 1, "python-101");
            createLearner("L1", "python-101");
            createLearner("L2");

            enroll("C1", "L1");
            MvcResult resp = enroll("C1", "L2");

            assertThat(code(resp)).isEqualTo(422);
        }

        /**
         * Standard FIFO promotion over two successive drops when everyone is eligible. The
         * "ineligible learner is skipped" scenario needs state the HTTP API cannot create, so it is
         * covered at service level in {@link EnrollmentServiceTests}.
         */
        @Test
        void fifoPromotionContinuesInOrderWhenAllWaitlistedLearnersAreEligible() throws Exception {
            createCourse("C1", 1, "python-101");
            for (String lid : List.of("L1", "L2", "L3")) {
                createLearner(lid, "python-101");
            }
            enroll("C1", "L1");
            enroll("C1", "L2");
            enroll("C1", "L3");

            assertThat(body(drop("C1", "L1")).get("promoted_learner_id").asText()).isEqualTo("L2");
            assertThat(body(drop("C1", "L2")).get("promoted_learner_id").asText()).isEqualTo("L3");
        }
    }

    // ================================================== 6. invalid drops

    @Nested
    class InvalidDropOperations {

        @Test
        void dropByNonEnrolledLearnerReturns404() throws Exception {
            createCourse("C1", 2);
            createLearner("L1");

            MvcResult resp = drop("C1", "L1");

            assertThat(code(resp)).isEqualTo(404);
            assertThat(body(resp).get("detail").asText().toLowerCase()).contains("not enrolled");
        }

        @Test
        void doubleDropSecondReturns404() throws Exception {
            createCourse("C1", 2);
            createLearner("L1");

            enroll("C1", "L1");
            drop("C1", "L1");

            assertThat(code(drop("C1", "L1"))).isEqualTo(404);
        }

        @Test
        void dropForNonexistentCourseReturns404() throws Exception {
            createLearner("L1");
            assertThat(code(drop("GHOST", "L1"))).isEqualTo(404);
        }

        @Test
        void dropForNonexistentLearnerReturns404() throws Exception {
            createCourse("C1", 2);
            assertThat(code(drop("C1", "GHOST"))).isEqualTo(404);
        }
    }

    // ================================================== 7. capacity invariants

    @Nested
    class CapacityInvariants {

        @Test
        void enrolledCountNeverExceedsCapacity() throws Exception {
            createCourse("C1", 2);
            for (int i = 1; i <= 5; i++) {
                createLearner("L" + i);
                enroll("C1", "L" + i);
            }

            JsonNode s = courseStatus("C1");
            assertThat(s.get("enrolled_count").asInt()).isEqualTo(2);
            assertThat(s.get("available_seats").asInt()).isEqualTo(0);
            assertThat(s.get("waitlist")).hasSize(3);
        }

        @Test
        void capacityBoundaryExactlyAtLimit() throws Exception {
            createCourse("C1", 3);
            for (int i = 1; i <= 4; i++) {
                createLearner("L" + i);
            }
            for (int i = 1; i <= 3; i++) {
                assertThat(body(enroll("C1", "L" + i)).get("status").asText()).isEqualTo("enrolled");
            }

            JsonNode b = body(enroll("C1", "L4"));
            assertThat(b.get("status").asText()).isEqualTo("waitlisted");
            assertThat(b.get("position").asInt()).isEqualTo(1);
        }
    }

    // ================================================== 8. not found + validation

    @Nested
    class NotFoundAndValidation {

        @Test
        void enrollIntoNonexistentCourseReturns404() throws Exception {
            createLearner("L1");
            assertThat(code(enroll("NO_COURSE", "L1"))).isEqualTo(404);
        }

        @Test
        void enrollNonexistentLearnerReturns404() throws Exception {
            createCourse("C1", 2);
            assertThat(code(enroll("C1", "NO_LEARNER"))).isEqualTo(404);
        }

        @Test
        void statusOfNonexistentCourseReturns404() throws Exception {
            mvc.perform(get("/api/v1/courses/GHOST/status")).andExpect(status().isNotFound());
        }

        @Test
        void courseWithNonPositiveCapacityReturns422() throws Exception {
            String json = mapper.writeValueAsString(
                    Map.of("id", "C1", "name", "Bad", "capacity", 0, "prerequisites", List.of()));
            mvc.perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void enrollWithoutLearnerIdReturns422() throws Exception {
            createCourse("C1", 2);
            mvc.perform(post("/api/v1/courses/C1/enroll").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }
}
