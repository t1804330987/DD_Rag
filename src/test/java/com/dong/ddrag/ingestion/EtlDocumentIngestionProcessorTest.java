package com.dong.ddrag.ingestion;

import com.dong.ddrag.common.exception.BusinessException;
import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.model.entity.DocumentEntity;
import com.dong.ddrag.ingestion.chunk.ChunkService;
import com.dong.ddrag.ingestion.model.entity.DocumentChunkEntity;
import com.dong.ddrag.ingestion.parser.factory.DocumentParserFactory;
import com.dong.ddrag.ingestion.parser.strategy.DocumentParser;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;
import com.dong.ddrag.ingestion.service.EtlDocumentIngestionProcessor;
import com.dong.ddrag.ingestion.transformer.StructureAwareChunkTransformer;
import com.dong.ddrag.ingestion.transformer.TextCleanupTransformer;
import com.dong.ddrag.ingestion.vector.VectorIngestionService;
import com.dong.ddrag.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EtlDocumentIngestionProcessorTest {

    private static final Long DOCUMENT_ID = 11L;
    private static final Long GROUP_ID = 7L;
    private static final String PROCESSOR_CONFIGURATION_CLASS_NAME =
            "com.dong.ddrag.ingestion.config.DocumentIngestionConfiguration";

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ObjectStorageService storageService;

    @Mock
    private DocumentParserFactory parserFactory;

    @Mock
    private DocumentParser parser;

    @Mock
    private TextCleanupTransformer textCleanupTransformer;

    @Mock
    private StructureAwareChunkTransformer chunkTransformer;

    @Mock
    private ChunkService chunkService;

    @Mock
    private VectorIngestionService vectorService;

    @InjectMocks
    private EtlDocumentIngestionProcessor processor;

    @Test
    void shouldReadCleanChunkPersistAndIngestDocument() {
        DocumentEntity documentEntity = createDocumentEntity();
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream("raw markdown".getBytes(StandardCharsets.UTF_8));
        Document cleanedDocument = Document.builder().text("cleaned body").build();
        Document chunkDocument = Document.builder().text("chunk body").build();
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        givenDocumentQueryReturns(documentEntity);
        given(storageService.getObject("knowledge-bucket", "group-7/doc-11.md")).willReturn(inputStream);
        given(parserFactory.getParser("md")).willReturn(parser);
        given(parser.parse(inputStream)).willReturn("parsed body");
        given(textCleanupTransformer.apply(ArgumentMatchers.anyList())).willReturn(List.of(cleanedDocument));
        given(documentMapper.updatePreviewText(DOCUMENT_ID, GROUP_ID, "cleaned body")).willReturn(1);
        given(chunkTransformer.apply(List.of(cleanedDocument))).willReturn(List.of(chunkDocument));
        given(chunkService.saveChunkDocuments(DOCUMENT_ID, GROUP_ID, List.of(chunkDocument)))
                .willReturn(List.of(chunk));

        processor.process(DOCUMENT_ID, GROUP_ID);

        verify(documentMapper).selectByIdAndGroupId(DOCUMENT_ID, GROUP_ID);
        verify(storageService).getObject("knowledge-bucket", "group-7/doc-11.md");
        verify(parserFactory).getParser("md");
        verify(parser).parse(inputStream);
        ArgumentCaptor<List<Document>> rawDocumentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(textCleanupTransformer).apply(rawDocumentsCaptor.capture());
        verify(chunkTransformer).apply(List.of(cleanedDocument));
        verify(chunkService).saveChunkDocuments(DOCUMENT_ID, GROUP_ID, List.of(chunkDocument));
        verify(vectorService).ingestChunks(List.of(chunk));

        List<Document> rawDocuments = rawDocumentsCaptor.getValue();
        assertThat(rawDocuments).hasSize(1);
        Document rawDocument = rawDocuments.getFirst();
        assertThat(rawDocument.getText()).isEqualTo("parsed body");
        assertThat(rawDocument.getMetadata()).containsAllEntriesOf(Map.of(
                "groupId", GROUP_ID,
                "documentId", DOCUMENT_ID,
                "fileName", "notes.md",
                "source", "minio://knowledge-bucket/group-7/doc-11.md"
        ));
        verify(documentMapper).updatePreviewText(DOCUMENT_ID, GROUP_ID, "cleaned body");
    }

    @Test
    void shouldPersistFirst200CharactersOfCleanedPreviewText() {
        DocumentEntity documentEntity = createDocumentEntity();
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream("raw markdown".getBytes(StandardCharsets.UTF_8));
        String longText = "a".repeat(210);
        Document cleanedDocument = Document.builder().text(longText).build();
        givenDocumentQueryReturns(documentEntity);
        given(storageService.getObject("knowledge-bucket", "group-7/doc-11.md")).willReturn(inputStream);
        given(parserFactory.getParser("md")).willReturn(parser);
        given(parser.parse(inputStream)).willReturn("parsed body");
        given(textCleanupTransformer.apply(ArgumentMatchers.anyList())).willReturn(List.of(cleanedDocument));
        given(documentMapper.updatePreviewText(DOCUMENT_ID, GROUP_ID, "a".repeat(200))).willReturn(1);
        given(chunkTransformer.apply(List.of(cleanedDocument))).willReturn(List.of());
        given(chunkService.saveChunkDocuments(DOCUMENT_ID, GROUP_ID, List.of())).willReturn(List.of());

        processor.process(DOCUMENT_ID, GROUP_ID);

        verify(documentMapper).updatePreviewText(DOCUMENT_ID, GROUP_ID, "a".repeat(200));
    }

    @Test
    void shouldThrowBusinessExceptionWhenDocumentDoesNotExist() {
        given(documentMapper.selectByIdAndGroupId(DOCUMENT_ID, GROUP_ID)).willReturn(null);

        assertThatThrownBy(() -> processor.process(DOCUMENT_ID, GROUP_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("待入库文档不存在");

        verifyNoInteractions(storageService, parserFactory, parser, chunkService, vectorService);
    }

    @Test
    void shouldRegisterEtlProcessorWhenVectorIngestionServiceExists() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        EtlProcessorDependenciesConfiguration.class,
                        loadProcessorConfiguration()
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DocumentIngestionProcessor.class);
                    assertThat(context).hasSingleBean(TextCleanupTransformer.class);
                    assertThat(context.getBean(DocumentIngestionProcessor.class))
                            .isInstanceOf(EtlDocumentIngestionProcessor.class);
                });
    }

    @Test
    void shouldFailFastWhenVectorIngestionServiceMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        MissingVectorIngestionDependenciesConfiguration.class,
                        loadProcessorConfiguration()
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("VectorIngestionService");
                });
    }

    @Test
    void shouldFailFastWhenNoDependenciesExist() {
        new ApplicationContextRunner()
                .withUserConfiguration(loadProcessorConfiguration())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("DocumentMapper");
                });
    }

    @Test
    void shouldFailFastWhenChunkServiceMissing() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        MissingChunkServiceDependenciesConfiguration.class,
                        loadProcessorConfiguration()
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("ChunkService");
                });
    }

    private void givenDocumentQueryReturns(DocumentEntity documentEntity) {
        given(documentMapper.selectByIdAndGroupId(DOCUMENT_ID, GROUP_ID)).willReturn(documentEntity);
    }

    private DocumentEntity createDocumentEntity() {
        DocumentEntity documentEntity = new DocumentEntity();
        documentEntity.setId(DOCUMENT_ID);
        documentEntity.setGroupId(GROUP_ID);
        documentEntity.setFileName("notes.md");
        documentEntity.setFileExt("md");
        documentEntity.setStorageBucket("knowledge-bucket");
        documentEntity.setStorageObjectKey("group-7/doc-11.md");
        return documentEntity;
    }

    private static Class<?> loadProcessorConfiguration() {
        try {
            return Class.forName(PROCESSOR_CONFIGURATION_CLASS_NAME);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("无法加载 DocumentIngestionConfiguration", exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EtlProcessorDependenciesConfiguration {

        @Bean
        DocumentMapper documentMapper() {
            return mock(DocumentMapper.class);
        }

        @Bean
        ObjectStorageService objectStorageService() {
            return mock(ObjectStorageService.class);
        }

        @Bean
        DocumentParserFactory documentParserFactory() {
            return mock(DocumentParserFactory.class);
        }

        @Bean
        StructureAwareChunkTransformer structureAwareChunkTransformer() {
            return mock(StructureAwareChunkTransformer.class);
        }

        @Bean
        ChunkService chunkService() {
            return mock(ChunkService.class);
        }

        @Bean
        VectorIngestionService vectorIngestionService() {
            return mock(VectorIngestionService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingChunkServiceDependenciesConfiguration {

        @Bean
        DocumentMapper documentMapper() {
            return mock(DocumentMapper.class);
        }

        @Bean
        ObjectStorageService objectStorageService() {
            return mock(ObjectStorageService.class);
        }

        @Bean
        DocumentParserFactory documentParserFactory() {
            return mock(DocumentParserFactory.class);
        }

        @Bean
        StructureAwareChunkTransformer structureAwareChunkTransformer() {
            return mock(StructureAwareChunkTransformer.class);
        }

        @Bean
        VectorIngestionService vectorIngestionService() {
            return mock(VectorIngestionService.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MissingVectorIngestionDependenciesConfiguration {

        @Bean
        DocumentMapper documentMapper() {
            return mock(DocumentMapper.class);
        }

        @Bean
        ObjectStorageService objectStorageService() {
            return mock(ObjectStorageService.class);
        }

        @Bean
        DocumentParserFactory documentParserFactory() {
            return mock(DocumentParserFactory.class);
        }

        @Bean
        StructureAwareChunkTransformer structureAwareChunkTransformer() {
            return mock(StructureAwareChunkTransformer.class);
        }

        @Bean
        ChunkService chunkService() {
            return mock(ChunkService.class);
        }
    }
}
