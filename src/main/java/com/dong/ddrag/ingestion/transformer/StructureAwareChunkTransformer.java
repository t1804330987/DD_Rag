package com.dong.ddrag.ingestion.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StructureAwareChunkTransformer implements DocumentTransformer {
    private static final String STRATEGY = "structure-aware-token-budget-v1";
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");
    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})\\s+(.+)$");
    private static final int CHARS_PER_TOKEN = 1;
    private final ChunkingProperties properties;

    public StructureAwareChunkTransformer(ChunkingProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<Document> apply(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<Document> chunks = new ArrayList<>();
        for (Document document : documents) {
            chunks.addAll(chunkDocument(document));
        }
        return chunks;
    }

    private List<Document> chunkDocument(Document document) {
        if (document == null || document.getText() == null || document.getText().isBlank()) {
            return List.of();
        }
        String text = document.getText();
        List<ChunkRange> ranges = splitBySections(text).stream()
                .flatMap(section -> splitSection(text, section).stream())
                .toList();
        return buildDocuments(document, ranges);
    }

    private List<Section> splitBySections(String text) {
        List<HeadingMatch> headings = collectHeadings(text);
        if (headings.isEmpty()) {
            return List.of(new Section(0, text.length(), ""));
        }
        List<Section> sections = new ArrayList<>();
        appendLeadingSection(text, headings.getFirst().start(), sections);
        for (int index = 0; index < headings.size(); index++) {
            HeadingMatch heading = headings.get(index);
            int end = index + 1 < headings.size() ? headings.get(index + 1).start() : text.length();
            appendSection(heading.start(), end, heading.title(), text, sections);
        }
        return sections;
    }

    private List<HeadingMatch> collectHeadings(String text) {
        List<HeadingMatch> headings = new ArrayList<>();
        Fence fence = null;
        for (int start = 0; start < text.length(); ) {
            int end = text.indexOf('\n', start);
            end = end >= 0 ? end : text.length();
            String line = text.substring(start, end);
            if (fence == null) {
                fence = openFence(line);
                if (fence == null) {
                    HeadingMatch heading = parseHeading(line, start);
                    if (heading != null) {
                        headings.add(heading);
                    }
                }
            } else if (isClosingFence(line, fence)) {
                fence = null;
            }
            start = end < text.length() ? end + 1 : text.length();
        }
        return headings;
    }

    private void appendLeadingSection(String text, int firstHeadingStart, List<Section> sections) {
        if (firstHeadingStart <= 0) return;
        Range range = trimRange(text, 0, firstHeadingStart);
        if (range != null) sections.add(new Section(range.start(), range.end(), ""));
    }

    private void appendSection(int start, int end, String title, String text, List<Section> sections) {
        Range range = trimRange(text, start, end);
        if (range != null) sections.add(new Section(range.start(), range.end(), title));
    }

    private List<ChunkRange> splitSection(String text, Section section) {
        return estimateTokens(text.substring(section.start(), section.end())) <= maxTokens()
                ? List.of(section.toChunkRange())
                : mergePieces(text, splitOversizedPieces(text, section));
    }

    private List<ChunkRange> splitOversizedPieces(String text, Section section) {
        List<ChunkRange> pieces = new ArrayList<>();
        for (ChunkRange paragraph : splitByParagraphs(text, section)) {
            String paragraphText = text.substring(paragraph.start(), paragraph.end());
            pieces.addAll(estimateTokens(paragraphText) <= maxTokens()
                    ? List.of(paragraph)
                    : splitBySentences(text, paragraph));
        }
        return splitRemainingOversized(text, pieces);
    }

    private List<ChunkRange> splitByParagraphs(String text, Section section) {
        Matcher matcher = BLANK_LINES.matcher(text.substring(section.start(), section.end()));
        List<ChunkRange> paragraphs = new ArrayList<>();
        int cursor = section.start();
        while (matcher.find()) {
            appendRange(text, cursor, section.start() + matcher.start(), section.path(), section.start(), paragraphs);
            cursor = section.start() + matcher.end();
        }
        appendRange(text, cursor, section.end(), section.path(), section.start(), paragraphs);
        return paragraphs;
    }

    private List<ChunkRange> splitBySentences(String text, ChunkRange range) {
        List<ChunkRange> sentences = new ArrayList<>();
        int cursor = range.start();
        for (int index = range.start(); index < range.end(); index++) {
            if (isSentenceBoundary(text.charAt(index))) {
                appendRange(text, cursor, index + 1, range.path(), range.sectionStart(), sentences);
                cursor = index + 1;
            }
        }
        appendRange(text, cursor, range.end(), range.path(), range.sectionStart(), sentences);
        return sentences;
    }

    private List<ChunkRange> splitRemainingOversized(String text, List<ChunkRange> pieces) {
        List<ChunkRange> ranges = new ArrayList<>();
        for (ChunkRange piece : pieces) {
            boolean fits = estimateTokens(text.substring(piece.start(), piece.end())) <= maxTokens();
            ranges.addAll(fits ? List.of(piece) : splitByTokenBudget(text, piece));
        }
        return ranges;
    }

    private List<ChunkRange> splitByTokenBudget(String text, ChunkRange range) {
        List<ChunkRange> chunks = new ArrayList<>();
        int maxChars = Math.max(CHARS_PER_TOKEN, maxTokens() * CHARS_PER_TOKEN);
        for (int cursor = range.start(); cursor < range.end(); cursor += maxChars) {
            appendRange(text, cursor, Math.min(range.end(), cursor + maxChars), range.path(),
                    range.sectionStart(), chunks);
        }
        return chunks;
    }

    private List<ChunkRange> mergePieces(String text, List<ChunkRange> pieces) {
        List<ChunkRange> chunks = new ArrayList<>();
        ChunkRange current = null;
        for (ChunkRange piece : pieces) {
            if (current == null) {
                current = piece;
            } else if (canMerge(text, current, piece)) {
                current = new ChunkRange(current.start(), piece.end(), current.path(), current.sectionStart());
            } else {
                chunks.add(current);
                current = piece;
            }
        }
        if (current != null) {
            chunks.add(current);
        }
        return chunks;
    }

    private boolean canMerge(String text, ChunkRange current, ChunkRange next) {
        String candidate = text.substring(current.start(), next.end());
        int currentTokens = estimateTokens(text.substring(current.start(), current.end()));
        return estimateTokens(candidate) <= targetTokens()
                || currentTokens < targetTokens() && estimateTokens(candidate) <= maxTokens();
    }

    private List<Document> buildDocuments(Document source, List<ChunkRange> ranges) {
        List<Document> chunks = new ArrayList<>();
        for (ChunkRange range : ranges) {
            int start = chunks.isEmpty() ? range.start() : overlapStart(range.start(), range.sectionStart());
            Range chunkRange = trimRange(source.getText(), start, range.end());
            if (chunkRange != null) {
                chunks.add(buildDocument(source, chunkRange, range.path(), chunks.size()));
            }
        }
        return chunks;
    }

    private Document buildDocument(Document source, Range range, String sectionPath, int chunkIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (source.getMetadata() != null) {
            metadata.putAll(source.getMetadata());
        }
        metadata.put("sectionPath", sectionPath);
        metadata.put("charStart", range.start());
        metadata.put("charEnd", range.end());
        metadata.put("chunkStrategy", STRATEGY);
        String id = source.getId() == null ? null : source.getId() + ":" + chunkIndex;
        return Document.builder()
                .id(id)
                .text(source.getText().substring(range.start(), range.end()))
                .metadata(metadata)
                .build();
    }

    private int overlapStart(int start, int sectionStart) {
        if (properties.getOverlapTokens() <= 0) return start;
        return Math.max(sectionStart, start - properties.getOverlapTokens() * CHARS_PER_TOKEN);
    }

    private void appendRange(String text, int start, int end, String path,
                             int sectionStart, List<ChunkRange> ranges) {
        Range range = trimRange(text, start, end);
        if (range != null) ranges.add(new ChunkRange(range.start(), range.end(), path, sectionStart));
    }

    private Range trimRange(String text, int start, int end) {
        int normalizedStart = Math.max(0, start);
        int normalizedEnd = Math.min(text.length(), end);
        while (normalizedStart < normalizedEnd && Character.isWhitespace(text.charAt(normalizedStart))) {
            normalizedStart++;
        }
        while (normalizedEnd > normalizedStart && Character.isWhitespace(text.charAt(normalizedEnd - 1))) {
            normalizedEnd--;
        }
        return normalizedStart < normalizedEnd ? new Range(normalizedStart, normalizedEnd) : null;
    }

    private boolean isSentenceBoundary(char character) { return "。！？；!?;".indexOf(character) >= 0; }
    private String cleanHeading(String title) { return title.replaceAll("\\s+#*$", "").strip(); }

    private HeadingMatch parseHeading(String line, int start) {
        Matcher matcher = HEADING.matcher(line);
        return matcher.matches() ? new HeadingMatch(start, cleanHeading(matcher.group(2))) : null;
    }

    private Fence openFence(String line) {
        int indent = leadingSpaces(line);
        if (indent > 3 || indent == line.length()) {
            return null;
        }
        char marker = line.charAt(indent);
        int length = fenceLength(line, indent, marker);
        return (marker == '`' || marker == '~') && length >= 3 ? new Fence(marker, length) : null;
    }

    private boolean isClosingFence(String line, Fence fence) {
        int indent = leadingSpaces(line);
        if (indent > 3 || indent == line.length() || line.charAt(indent) != fence.marker()) {
            return false;
        }
        int length = fenceLength(line, indent, fence.marker());
        return length >= fence.length() && line.substring(indent + length).isBlank();
    }

    private int leadingSpaces(String line) {
        int index = 0;
        while (index < line.length() && line.charAt(index) == ' ') index++;
        return index;
    }
    private int fenceLength(String line, int start, char marker) {
        int index = start;
        while (index < line.length() && line.charAt(index) == marker) index++;
        return index - start;
    }
    private int targetTokens() { return Math.max(1, properties.getTargetTokens()); }
    private int maxTokens() { return Math.max(targetTokens(), properties.getMaxTokens()); }
    private int estimateTokens(String text) { return Math.max(1, text.length()); }

    private record HeadingMatch(int start, String title) {}
    private record Range(int start, int end) {}
    private record Fence(char marker, int length) {}
    private record Section(int start, int end, String path) {
        private ChunkRange toChunkRange() { return new ChunkRange(start, end, path, start); }
    }
    private record ChunkRange(int start, int end, String path, int sectionStart) {}
}
