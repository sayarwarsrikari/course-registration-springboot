package com.example.courseregistration.dto;

/** One waitlist row as seen from the status endpoint (position 1 = next in line). */
public record WaitlistEntryResponse(String learnerId, int position) {}
