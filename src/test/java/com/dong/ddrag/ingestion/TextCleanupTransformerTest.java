package com.dong.ddrag.ingestion;

import com.dong.ddrag.ingestion.transformer.TextCleanupTransformer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextCleanupTransformerTest {

    @Test
    void shouldCleanupTextAndPreserveMetadata() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Document source = Document.builder()
                .id("doc-3001")
                .text("# 标题\r\n\r\n\r\n-  条目一\t\t内容\u0007\r\n正文   内容")
                .metadata("documentId", 3001L)
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned.getText()).isEqualTo("# 标题\n\n- 条目一 内容\n正文 内容");
        assertThat(cleaned.getMetadata()).containsEntry("documentId", 3001L);
    }

    @Test
    void shouldPreserveUpToTwoLeadingAndTrailingBlankLines() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Document source = Document.builder()
                .text("\n\n  第一行  \n第二\t\t行\n\n")
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned.getText()).isEqualTo("\n\n第一行\n第二 行\n\n");
    }

    @Test
    void shouldPreserveDeleteCharacterAndRemovePlannedControlCharacters() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Document source = Document.builder()
                .text("保留\u007F字符\u0007 和内容")
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned.getText()).isEqualTo("保留\u007F字符 和内容");
    }

    @Test
    void shouldPreserveWhitespaceInsideFencedCodeBlockWhileCleaningNormalLines() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Document source = Document.builder()
                .text("  普通\t\t文本 \u0007\r\n\r\n```java  \r\n\tif (x  > 0) {\r\n    return  x;\u0007\r\n}\r\n```  \r\n  结尾\t\t文本  ")
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned.getText()).isEqualTo("普通 文本\n\n```java  \n\tif (x  > 0) {\n    return  x;\n}\n```  \n结尾 文本");
    }

    @Test
    void shouldPreserveIndentedFenceLinesAsOriginalText() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Document source = Document.builder()
                .text("- 列表项\r\n\r\n  ```java\r\n  System.out.println(1);\r\n  ``` \r\n  收尾\t\t文本")
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned.getText()).isEqualTo("- 列表项\n\n  ```java\n  System.out.println(1);\n  ``` \n收尾 文本");
    }

    @Test
    void shouldNotCloseLongerFenceWhenInnerFenceIsShorter() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Document source = Document.builder()
                .text("前文\r\n\r\n````markdown\r\n```inner\r\ncode  line\t\tkept\r\n````\r\n  后文\t\t文本  ")
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned.getText()).isEqualTo("""
                前文

                ````markdown
                ```inner
                code  line\t\tkept
                ````
                后文 文本""");
    }

    @Test
    void shouldNotCloseFenceWithIndentedFenceLikeLine() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Document source = Document.builder()
                .text("```java\r\ncode  line\r\n    ```example\r\nmore\t\tcode\r\n```\r\n尾部")
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned.getText()).isEqualTo("""
                ```java
                code  line
                    ```example
                more\t\tcode
                ```
                尾部""");
    }

    @Test
    void shouldNotCloseFenceWhenMarkerChanges() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Document source = Document.builder()
                .text("~~~sql\r\nSELECT  *\r\n```\r\nFROM dual;\r\n~~~\r\n  尾部\t\t文本")
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned.getText()).isEqualTo("""
                ~~~sql
                SELECT  *
                ```
                FROM dual;
                ~~~
                尾部 文本""");
    }

    @Test
    void shouldReturnNonTextDocumentUnchanged() {
        TextCleanupTransformer transformer = new TextCleanupTransformer();
        Media media = Media.builder()
                .mimeType(MimeTypeUtils.IMAGE_PNG)
                .data(new ByteArrayResource(new byte[]{1, 2, 3}))
                .id("media-1")
                .name("image.png")
                .build();
        Document source = Document.builder()
                .id("doc-media")
                .media(media)
                .metadata("documentId", "media-doc")
                .build();

        Document cleaned = transformer.apply(List.of(source)).getFirst();

        assertThat(cleaned).isSameAs(source);
        assertThat(cleaned.getText()).isNull();
        assertThat(cleaned.getMedia()).isSameAs(media);
        assertThat(cleaned.getMetadata()).containsEntry("documentId", "media-doc");
    }
}
