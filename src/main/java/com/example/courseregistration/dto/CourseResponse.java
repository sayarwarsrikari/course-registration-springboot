package com.example.courseregistration.dto;

import java.util.List;

public record CourseResponse(String id, String name, int capacity, List<String> prerequisites) {}
