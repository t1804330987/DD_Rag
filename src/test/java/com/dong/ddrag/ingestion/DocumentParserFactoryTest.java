package com.dong.ddrag.ingestion;

import com.dong.ddrag.ingestion.parser.strategy.MdDocumentParser;
import com.dong.ddrag.ingestion.parser.strategy.TxtDocumentParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentParserFactoryTest {

    private static final String FACTORY_CLASS =
            "com.dong.ddrag.ingestion.parser.factory.DocumentParserFactory";

    @Test
    void shouldReturnTxtParserForTxtExtension() throws Exception {
        assertThat(resolveParserClassName("txt")).isEqualTo("TxtDocumentParser");
    }

    @Test
    void shouldReturnMdParserForMdExtension() throws Exception {
        assertThat(resolveParserClassName("md")).isEqualTo("MdDocumentParser");
    }

    @Test
    void shouldReturnPdfParserForPdfExtension() throws Exception {
        assertThat(resolveParserClassName("pdf")).isEqualTo("PdfDocumentParser");
    }

    @Test
    void shouldReturnDocxParserForDocxExtension() throws Exception {
        assertThat(resolveParserClassName("docx")).isEqualTo("DocxDocumentParser");
    }

    @Test
    void shouldThrowForUnsupportedExtension() {
        assertThatThrownBy(() -> resolveParserClassName("xlsx"))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldParseUtf8BomTxtContent() {
        TxtDocumentParser parser = new TxtDocumentParser();
        byte[] content = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] textBytes = "第一行\r\n第二行".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[content.length + textBytes.length];
        System.arraycopy(content, 0, bytes, 0, content.length);
        System.arraycopy(textBytes, 0, bytes, content.length, textBytes.length);

        String parsed = parser.parse(new ByteArrayInputStream(bytes));

        assertThat(parsed).isEqualTo("第一行\n第二行");
    }

    @Test
    void shouldParseUtf16LeBomMarkdownContent() {
        MdDocumentParser parser = new MdDocumentParser();
        byte[] bytes = withBom("# 标题\r\n- 列表项", StandardCharsets.UTF_16LE, (byte) 0xFF, (byte) 0xFE);

        String parsed = parser.parse(new ByteArrayInputStream(bytes));

        assertThat(parsed).isEqualTo("标题\n列表项");
    }

    @Test
    void shouldParseGbkTxtContentWithoutBom() {
        TxtDocumentParser parser = new TxtDocumentParser();
        byte[] invalidBytes = "中文内容".getBytes(Charset.forName("GBK"));

        String parsed = parser.parse(new ByteArrayInputStream(invalidBytes));

        assertThat(parsed).isEqualTo("中文内容");
    }

    @Test
    @DisplayName("应明确拒绝伪 DOCX 文件，而不是返回笼统解析失败")
    void shouldRejectInvalidDocxPayloadWithClearMessage() throws Exception {
        Object parser = resolveParser("docx");
        Method parseMethod = parser.getClass().getMethod("parse", java.io.InputStream.class);

        assertThatThrownBy(() -> parseMethod.invoke(parser, new ByteArrayInputStream("not-a-docx".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(InvocationTargetException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .cause()
                .hasMessageContaining("无效 DOCX")
                .hasMessageContaining("文件内容不是合法的 Word 文档");
    }

    private String resolveParserClassName(String extension) throws Exception {
        Object parser = resolveParser(extension);
        return parser.getClass().getSimpleName();
    }

    private Object resolveParser(String extension) throws Exception {
        Class<?> factoryClass = Class.forName(FACTORY_CLASS);
        Object factory = factoryClass.getDeclaredConstructor().newInstance();
        Method method = factoryClass.getMethod("getParser", String.class);
        return method.invoke(factory, extension);
    }

    private byte[] withBom(String content, Charset charset, byte firstBomByte, byte secondBomByte) {
        byte[] textBytes = content.getBytes(charset);
        byte[] bytes = new byte[textBytes.length + 2];
        bytes[0] = firstBomByte;
        bytes[1] = secondBomByte;
        System.arraycopy(textBytes, 0, bytes, 2, textBytes.length);
        return bytes;
    }
}
