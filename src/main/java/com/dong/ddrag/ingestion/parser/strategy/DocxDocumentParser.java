package com.dong.ddrag.ingestion.parser.strategy;

import com.dong.ddrag.common.exception.BusinessException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

public class DocxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String extension) {
        return "docx".equalsIgnoreCase(extension);
    }

    @Override
    public String parse(InputStream inputStream) {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().replace("\r\n", "\n").replace('\r', '\n').trim();
        } catch (NotOfficeXmlFileException | ZipException exception) {
            throw new BusinessException("无效 DOCX 文件：文件内容不是合法的 Word 文档", exception);
        } catch (IOException exception) {
            throw new BusinessException("DOCX 文档解析失败", exception);
        }
    }
}
