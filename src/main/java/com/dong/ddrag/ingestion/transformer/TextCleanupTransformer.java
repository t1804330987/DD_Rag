package com.dong.ddrag.ingestion.transformer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextCleanupTransformer implements DocumentTransformer {

    private static final int MAX_FENCE_INDENT = 3;
    private static final int MIN_FENCE_LENGTH = 3;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private static final Pattern INLINE_WHITESPACE = Pattern.compile("[ \\t]+");
    private static final Pattern EXCESSIVE_BLANK_LINES = Pattern.compile("\\n{3,}");

    @Override
    public List<Document> apply(List<Document> documents) {
        return documents.stream()
                .map(this::cleanupDocument)
                .toList();
    }

    private Document cleanupDocument(Document document) {
        if (document.getText() == null) {
            return document;
        }
        return document.mutate()
                .text(clean(document.getText()))
                .build();
    }

    String clean(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        normalized = CONTROL_CHARACTERS.matcher(normalized).replaceAll("");
        return cleanLines(normalized);
    }

    private String normalizeLine(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        return INLINE_WHITESPACE.matcher(trimmed).replaceAll(" ");
    }

    private String cleanLines(String normalized) {
        List<String> segments = new ArrayList<>();
        List<String> plainLines = new ArrayList<>();
        List<String> fenceLines = null;
        FenceMarker openingFence = null;

        for (String line : normalized.split("\n", -1)) {
            if (openingFence != null) {
                if (isClosingFence(line, openingFence)) {
                    fenceLines.add(line);
                    segments.add(String.join("\n", fenceLines));
                    fenceLines = null;
                    openingFence = null;
                } else {
                    fenceLines.add(line);
                }
                continue;
            }

            FenceMarker candidateFence = parseOpeningFence(line);
            if (candidateFence != null) {
                appendPlainSegment(segments, plainLines);
                fenceLines = new ArrayList<>();
                fenceLines.add(line);
                openingFence = candidateFence;
                continue;
            }

            plainLines.add(line);
        }

        if (openingFence != null && fenceLines != null) {
            segments.add(String.join("\n", fenceLines));
        }
        appendPlainSegment(segments, plainLines);
        return String.join("\n", segments);
    }

    private FenceMarker parseOpeningFence(String line) {
        int contentStart = countLeadingSpaces(line);
        if (contentStart < 0 || contentStart >= line.length()) {
            return null;
        }

        char marker = line.charAt(contentStart);
        int fenceLength = countFenceLength(line, contentStart, marker);
        if ((marker != '`' && marker != '~') || fenceLength < MIN_FENCE_LENGTH) {
            return null;
        }
        return new FenceMarker(marker, fenceLength);
    }

    private boolean isClosingFence(String line, FenceMarker openingFence) {
        int contentStart = countLeadingSpaces(line);
        if (contentStart < 0 || contentStart >= line.length()) {
            return false;
        }

        int fenceLength = countFenceLength(line, contentStart, openingFence.marker());
        return line.charAt(contentStart) == openingFence.marker()
                && fenceLength >= openingFence.length()
                && hasOnlyTrailingWhitespace(line, contentStart + fenceLength);
    }

    private int countLeadingSpaces(String line) {
        int leadingSpaces = 0;
        while (leadingSpaces < line.length() && line.charAt(leadingSpaces) == ' ') {
            leadingSpaces++;
        }
        return leadingSpaces <= MAX_FENCE_INDENT ? leadingSpaces : -1;
    }

    private int countFenceLength(String line, int startIndex, char marker) {
        int fenceLength = 0;
        while (startIndex + fenceLength < line.length() && line.charAt(startIndex + fenceLength) == marker) {
            fenceLength++;
        }
        return fenceLength;
    }

    private boolean hasOnlyTrailingWhitespace(String line, int startIndex) {
        for (int index = startIndex; index < line.length(); index++) {
            if (!Character.isWhitespace(line.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private void appendPlainSegment(List<String> segments, List<String> plainLines) {
        if (plainLines.isEmpty()) {
            return;
        }

        String normalizedPlainText = plainLines.stream()
                .map(this::normalizeLine)
                .collect(Collectors.joining("\n"));
        segments.add(EXCESSIVE_BLANK_LINES.matcher(normalizedPlainText).replaceAll("\n\n"));
        plainLines.clear();
    }

    private record FenceMarker(char marker, int length) {
    }
}
