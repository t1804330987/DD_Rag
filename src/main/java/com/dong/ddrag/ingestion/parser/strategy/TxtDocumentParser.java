package com.dong.ddrag.ingestion.parser.strategy;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.parser.support.TextDecodingSupport;

import java.io.InputStream;

public class TxtDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String extension) {
        return "txt".equalsIgnoreCase(extension);
    }

    @Override
    public String parse(InputStream inputStream) {
        return normalizeText(TextDecodingSupport.decode(inputStream, "TXT 文档解析失败"));
    }

    private String normalizeText(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
}
