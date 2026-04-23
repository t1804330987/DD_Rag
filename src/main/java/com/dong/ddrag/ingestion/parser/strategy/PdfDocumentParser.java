package com.dong.ddrag.ingestion.parser.strategy;

import com.dong.ddrag.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    @Override
    public String parse(InputStream inputStream) {
        try (PDDocument document = PDDocument.load(inputStream)) {
            String text = new PDFTextStripper().getText(document);
            return text.replace("\r\n", "\n").replace('\r', '\n').trim();
        } catch (IOException exception) {
            throw new BusinessException("PDF 文档解析失败", exception);
        }
    }
}
