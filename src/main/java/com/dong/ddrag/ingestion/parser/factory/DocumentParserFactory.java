package com.dong.ddrag.ingestion.parser.factory;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.ingestion.parser.strategy.DocxDocumentParser;
import com.dong.ddrag.ingestion.parser.strategy.DocumentParser;
import com.dong.ddrag.ingestion.parser.strategy.MdDocumentParser;
import com.dong.ddrag.ingestion.parser.strategy.PdfDocumentParser;
import com.dong.ddrag.ingestion.parser.strategy.TxtDocumentParser;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DocumentParserFactory {

    private final Map<String, DocumentParser> parserByExtension = new LinkedHashMap<>();

    public DocumentParserFactory() {
        this(List.of(
                new TxtDocumentParser(),
                new MdDocumentParser(),
                new PdfDocumentParser(),
                new DocxDocumentParser()
        ));
    }

    public DocumentParserFactory(List<DocumentParser> parsers) {
        for (DocumentParser parser : parsers) {
            register("txt", parser);
            register("md", parser);
            register("pdf", parser);
            register("docx", parser);
        }
    }

    public DocumentParser getParser(String extension) {
        String normalizedExtension = normalizeExtension(extension);
        DocumentParser parser = parserByExtension.get(normalizedExtension);
        if (parser == null) {
            throw new BusinessException("不支持的文档类型: " + normalizedExtension);
        }
        return parser;
    }

    private void register(String extension, DocumentParser parser) {
        if (parser.supports(extension)) {
            parserByExtension.put(extension, parser);
        }
    }

    private String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new BusinessException("文档扩展名不能为空");
        }
        return extension.replaceFirst("^\\.", "").toLowerCase(Locale.ROOT);
    }
}
