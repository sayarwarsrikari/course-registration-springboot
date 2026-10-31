package com.example.courseregistration.dto;

import java.util.List;

public record LearnerResponse(String id, String name, List<String> completedCourses) {}
