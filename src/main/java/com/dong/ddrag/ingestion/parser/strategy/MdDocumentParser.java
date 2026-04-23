package com.dong.ddrag.ingestion.parser.strategy;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.parser.support.TextDecodingSupport;

import java.io.InputStream;

public class MdDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String extension) {
        return "md".equalsIgnoreCase(extension);
    }

    @Override
    public String parse(InputStream inputStream) {
        return stripMarkdown(TextDecodingSupport.decode(inputStream, "Markdown 文档解析失败")).trim();
    }

    private String stripMarkdown(String content) {
        String plainText = content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^>\\s*", "")
                .replaceAll("(?m)^[-*+]\\s+", "")
                .replaceAll("(?m)^\\d+\\.\\s+", "")
                .replaceAll("(\\*\\*|__|[*_~])", "");
        return plainText.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n");
    }
}
