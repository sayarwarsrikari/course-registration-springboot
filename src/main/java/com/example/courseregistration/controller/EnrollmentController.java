package com.example.courseregistration.controller;

import com.example.courseregistration.dto.CourseCreateRequest;
import com.example.courseregistration.dto.CourseResponse;
import com.example.courseregistration.dto.CourseStatusResponse;
import com.example.courseregistration.dto.DropResponse;
import com.example.courseregistration.dto.EnrollRequest;
import com.example.courseregistration.dto.EnrollResponse;
import com.example.courseregistration.dto.LearnerCreateRequest;
import com.example.courseregistration.dto.LearnerResponse;
import com.example.courseregistration.dto.WaitlistEntryResponse;
import com.example.courseregistration.model.CourseOffering;
import com.example.courseregistration.model.Learner;
import com.example.courseregistration.service.EnrollmentService;
import com.example.courseregistration.service.EnrollmentService.CourseStatus;
import com.example.courseregistration.service.EnrollmentService.DropResult;
import com.example.courseregistration.service.EnrollmentService.EnrollResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP layer only: parse the request, call the service, shape the response. Domain exceptions are
 * translated to HTTP status codes centrally in {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1")
public class EnrollmentController {

    private final EnrollmentService service;

    public EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    // ----- administrative endpoints -----

    @PostMapping("/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "Admin")
    @Operation(summary = "Create a course offering")
    public CourseResponse createCourse(@Valid @RequestBody CourseCreateRequest body) {
        CourseOffering course =
                service.registerCourse(body.id(), body.name(), body.capacity(), body.prerequisites());
        return new CourseResponse(course.getId(), course.getName(), course.getCapacity(), course.getPrerequisites());
    }

    @PostMapping("/learners")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "Admin")
    @Operation(summary = "Register a learner")
    public LearnerResponse createLearner(@Valid @RequestBody LearnerCreateRequest body) {
        Learner learner = service.registerLearner(body.id(), body.name(), body.completedCourses());
        return new LearnerResponse(learner.id(), learner.name(), learner.completedCourses());
    }

    // ----- enrollment endpoints -----

    /**
     * 200 with status 'enrolled' or 'waitlisted' (the body carries the outcome).
     * 404 unknown course/learner; 409 duplicate enroll/waitlist; 422 prerequisites not met.
     */
    @PostMapping("/courses/{courseId}/enroll")
    @Tag(name = "Enrollment")
    @Operation(summary = "Enroll a learner in a course (or add to waitlist if full)")
    public EnrollResponse enrollLearner(
            @PathVariable("courseId") String courseId, @Valid @RequestBody EnrollRequest body) {
        EnrollResult result = service.enroll(courseId, body.learnerId());
        return new EnrollResponse(body.learnerId(), courseId, result.status(), result.position());
    }

    /** Drops the learner and auto-promotes the next eligible waitlisted learner (if any). */
    @DeleteMapping("/courses/{courseId}/enrollments/{learnerId}")
    @Tag(name = "Enrollment")
    @Operation(summary = "Drop a learner from a course")
    public DropResponse dropLearner(
            @PathVariable("courseId") String courseId, @PathVariable("learnerId") String learnerId) {
        DropResult result = service.drop(courseId, learnerId);
        return DropResponse.dropped(result.learnerId(), result.courseId(), result.promotedLearnerId());
    }

    @GetMapping("/courses/{courseId}/status")
    @Tag(name = "Enrollment")
    @Operation(summary = "View enrollment and waitlist status for a course")
    public CourseStatusResponse getCourseStatus(@PathVariable("courseId") String courseId) {
        CourseStatus s = service.getCourseStatus(courseId);
        return new CourseStatusResponse(
                s.courseId(),
                s.courseName(),
                s.capacity(),
                s.enrolledCount(),
                s.availableSeats(),
                s.enrolledLearnerIds(),
                s.waitlist().stream()
                        .map(w -> new WaitlistEntryResponse(w.learnerId(), w.position()))
                        .toList());
    }
}
