package com.ewos.shared.document;

/**
 * Classpath locations of the platform's embedded Unicode fonts (GNU FreeSans — see {@code
 * src/main/resources/fonts/README.md} for licensing). Centralizing these two constants is the first
 * step of extracting a shared PDF text-layout utility (Sprint 27A item 2, audit finding 8.2): every
 * PDF generator that needs full-Unicode text (Indian names, Devanagari, and other non-Latin-1
 * content — see {@link PdfFontLoader}) loads the same two files rather than each module hardcoding
 * its own resource path string.
 */
public final class EmbeddedFonts {

    public static final String FREE_SANS_REGULAR = "/fonts/FreeSans.ttf";
    public static final String FREE_SANS_BOLD = "/fonts/FreeSansBold.ttf";

    private EmbeddedFonts() {}
}
