# Bundled fonts

`FreeSans.ttf` / `FreeSansBold.ttf` — GNU FreeFont, used by
`com.ewos.exit.application.ExitDocumentPdfGenerationService` as the embedded PDF font (Sprint 26A
P1-3: the previous Standard-14 Helvetica font is a single-byte WinAnsiEncoding font that cannot
render Devanagari or other non-Latin-1 characters and throws when asked to; FreeSans is a full
Unicode TrueType font covering Latin Extended, Cyrillic, Greek, Devanagari, and many other scripts
— chosen for correctly rendering Indian names and other multilingual text in generated exit
documents).

License: GNU General Public License v3+ with the Font Embedding Exception (the exception
explicitly permits embedding/subsetting the font into generated documents — such as the PDFs this
service produces — without those documents becoming subject to the GPL). Source:
https://savannah.gnu.org/projects/freefont/
