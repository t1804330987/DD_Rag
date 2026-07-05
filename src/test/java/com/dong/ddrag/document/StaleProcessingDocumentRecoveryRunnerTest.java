package com.dong.ddrag.document;

import com.dong.ddrag.document.mapper.DocumentMapper;
import com.dong.ddrag.document.service.StaleProcessingDocumentRecoveryRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class StaleProcessingDocumentRecoveryRunnerTest {

    private static final String FAILURE_REASON = "文档处理任务因服务中断未完成，请重试。";

    @Mock
    private DocumentMapper documentMapper;

    @Test
    void shouldMarkAllProcessingDocumentsFailedOnStartup() throws Exception {
        given(documentMapper.markStaleProcessingDocumentsFailed(
                eq("PROCESSING"),
                eq("FAILED"),
                eq(FAILURE_REASON),
                isNull(),
                any(LocalDateTime.class)
        )).willReturn(2);

        StaleProcessingDocumentRecoveryRunner runner =
                new StaleProcessingDocumentRecoveryRunner(documentMapper, 30);

        runner.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<LocalDateTime> processedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(documentMapper).should().markStaleProcessingDocumentsFailed(
                eq("PROCESSING"),
                eq("FAILED"),
                eq(FAILURE_REASON),
                isNull(),
                processedAtCaptor.capture()
        );
        assertThat(processedAtCaptor.getValue()).isNotNull();
    }

    @Test
    void shouldIgnoreConfiguredTimeoutAndRecoverAllProcessingDocuments() throws Exception {
        StaleProcessingDocumentRecoveryRunner runner =
                new StaleProcessingDocumentRecoveryRunner(documentMapper, 0);

        runner.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<LocalDateTime> processedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(documentMapper).should().markStaleProcessingDocumentsFailed(
                eq("PROCESSING"),
                eq("FAILED"),
                eq(FAILURE_REASON),
                isNull(),
                processedAtCaptor.capture()
        );
        assertThat(processedAtCaptor.getValue()).isNotNull();
    }
}
