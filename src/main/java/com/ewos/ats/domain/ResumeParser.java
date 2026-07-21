package com.ewos.ats.domain;

/**
 * Contract for a resume-parsing plug-in. The default binding ({@code NoOpResumeParser}) never calls
 * a third-party OCR/NLP service; deployments that want structured resume extraction ship their own
 * {@code @Primary} bean pointing at their vendor (Sovren, Textkernel, HireAbility, ...).
 */
public interface ResumeParser {

    /** Returns {@code true} if this parser can handle the given MIME type. */
    boolean supports(String mimeType);

    /** Parse the resume payload; must not throw for the null / empty / unknown cases. */
    ParsedResume parse(byte[] content, String mimeType);

    /** Parser identifier + semver, written back to {@code candidate_resumes.parser_version}. */
    String parserVersion();
}
