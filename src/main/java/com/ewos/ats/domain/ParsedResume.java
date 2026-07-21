package com.ewos.ats.domain;

/**
 * Result of a resume-parse call. {@code rawText} is the extractor's text output; {@code
 * structuredJson} is an optional JSON serialization of skills, education, experience, etc. A no-op
 * parser returns both fields as {@code null}.
 */
public record ParsedResume(String rawText, String structuredJson) {

    public static final ParsedResume EMPTY = new ParsedResume(null, null);
}
