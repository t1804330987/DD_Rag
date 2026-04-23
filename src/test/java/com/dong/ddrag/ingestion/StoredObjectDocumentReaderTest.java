package com.dong.ddrag.ingestion;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.ingestion.parser.factory.DocumentParserFactory;
import com.dong.ddrag.ingestion.parser.strategy.DocumentParser;
import com.dong.ddrag.ingestion.reader.StoredObjectDocumentReader;
import com.dong.ddrag.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoredObjectDocumentReaderTest {

    @Test
    void shouldReadStoredObjectAndConvertItToSpringAiDocument() {
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        DocumentParserFactory parserFactory = mock(DocumentParserFactory.class);
        DocumentParser parser = mock(DocumentParser.class);
        DocumentEntity documentEntity = createDocumentEntity();
        TrackingInputStream inputStream =
                new TrackingInputStream("raw markdown".getBytes(StandardCharsets.UTF_8));
        when(storageService.getObject("knowledge-bucket", "group-7/doc-11.md")).thenReturn(inputStream);
        when(parserFactory.getParser("md")).thenReturn(parser);
        when(parser.parse(inputStream)).thenReturn("parsed body");

        StoredObjectDocumentReader reader =
                new StoredObjectDocumentReader(storageService, parserFactory, documentEntity);

        List<Document> documents = reader.read();

        assertThat(documents).hasSize(1);
        Document document = documents.getFirst();
        assertThat(document.getText()).isEqualTo("parsed body");
        assertThat(document.getMetadata())
                .containsEntry("groupId", 7L)
                .containsEntry("documentId", 11L)
                .containsEntry("fileName", "notes.md")
                .containsEntry("source", "minio://knowledge-bucket/group-7/doc-11.md");
        verify(storageService).getObject("knowledge-bucket", "group-7/doc-11.md");
        verify(parserFactory).getParser("md");
        verify(parser).parse(inputStream);
        assertThat(inputStream.closed).isTrue();
    }

    @Test
    void shouldRejectDocumentWithoutDocumentIdOrGroupId() {
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        DocumentParserFactory parserFactory = mock(DocumentParserFactory.class);
        DocumentEntity documentEntity = new DocumentEntity();
        documentEntity.setFileName("notes.md");
        documentEntity.setFileExt("md");
        documentEntity.setStorageBucket("knowledge-bucket");
        documentEntity.setStorageObjectKey("group-7/doc-11.md");

        StoredObjectDocumentReader reader =
                new StoredObjectDocumentReader(storageService, parserFactory, documentEntity);

        assertThatThrownBy(reader::read)
                .isInstanceOf(BusinessException.class)
                .hasMessage("读取文档前必须提供 documentId 和 groupId");
    }

    @Test
    void shouldWrapReadFailuresAsBusinessException() {
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        DocumentParserFactory parserFactory = mock(DocumentParserFactory.class);
        DocumentParser parser = mock(DocumentParser.class);
        DocumentEntity documentEntity = createDocumentEntity();
        InputStream inputStream = new BrokenInputStream();
        when(storageService.getObject("knowledge-bucket", "group-7/doc-11.md")).thenReturn(inputStream);
        when(parserFactory.getParser("md")).thenReturn(parser);
        when(parser.parse(inputStream)).thenThrow(new IllegalStateException("parse failed"));

        StoredObjectDocumentReader reader =
                new StoredObjectDocumentReader(storageService, parserFactory, documentEntity);

        assertThatThrownBy(reader::read)
                .isInstanceOf(BusinessException.class)
                .hasMessage("读取存储文档失败")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldPreserveBusinessExceptionFromStorageService() {
        ObjectStorageService storageService = mock(ObjectStorageService.class);
        DocumentParserFactory parserFactory = mock(DocumentParserFactory.class);
        DocumentEntity documentEntity = createDocumentEntity();
        when(storageService.getObject("knowledge-bucket", "group-7/doc-11.md"))
                .thenThrow(new BusinessException("对象存储未配置"));

        StoredObjectDocumentReader reader =
                new StoredObjectDocumentReader(storageService, parserFactory, documentEntity);

        assertThatThrownBy(reader::read)
                .isInstanceOf(BusinessException.class)
                .hasMessage("对象存储未配置");
    }

    private DocumentEntity createDocumentEntity() {
        DocumentEntity documentEntity = new DocumentEntity();
        documentEntity.setId(11L);
        documentEntity.setGroupId(7L);
        documentEntity.setFileName("notes.md");
        documentEntity.setFileExt("md");
        documentEntity.setStorageBucket("knowledge-bucket");
        documentEntity.setStorageObjectKey("group-7/doc-11.md");
        return documentEntity;
    }

    private static final class BrokenInputStream extends InputStream {

        @Override
        public int read() {
            return -1;
        }

        @Override
        public void close() throws IOException {
            throw new IOException("close failed");
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(byte[] buf) {
            super(buf);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
