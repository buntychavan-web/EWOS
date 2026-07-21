package com.ewos.ats.infrastructure.parsing;

import com.ewos.ats.domain.ParsedResume;
import com.ewos.ats.domain.ResumeParser;
import org.springframework.stereotype.Component;

/**
 * Default resume-parser binding. Ships in-tree so the ATS surface is functional out of the box
 * (resume upload works, the row is stored, {@code parsed = true} is honestly reported). Deployments
 * that need real extraction override this with a {@code @Primary} bean.
 */
@Component
public class NoOpResumeParser implements ResumeParser {

    private static final String VERSION = "noop-1.0";

    @Override
    public boolean supports(String mimeType) {
        return true;
    }

    @Override
    public ParsedResume parse(byte[] content, String mimeType) {
        return ParsedResume.EMPTY;
    }

    @Override
    public String parserVersion() {
        return VERSION;
    }
}
