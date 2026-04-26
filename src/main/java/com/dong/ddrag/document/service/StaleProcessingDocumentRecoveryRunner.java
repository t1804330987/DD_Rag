package com.dong.ddrag.document.service;

import com.dong.ddrag.common.enums.DocumentStatus;
import com.dong.ddrag.document.mapper.DocumentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 启动时回收遗留在 PROCESSING 的文档，避免服务中断后前端永远看到“处理中”。
 */
@Component
public class StaleProcessingDocumentRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleProcessingDocumentRecoveryRunner.class);
    private static final String DEFAULT_FAILURE_REASON = "文档处理任务因服务中断未完成，请重试。";

    private final DocumentMapper documentMapper;

    public StaleProcessingDocumentRecoveryRunner(
            DocumentMapper documentMapper,
            @Value("${document.ingestion.processing-timeout-minutes:30}") long staleTimeoutMinutes
    ) {
        this.documentMapper = documentMapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now();
        int recovered = documentMapper.markStaleProcessingDocumentsFailed(
                DocumentStatus.PROCESSING.name(),
                DocumentStatus.FAILED.name(),
                DEFAULT_FAILURE_REASON,
                null,
                now
        );
        if (recovered > 0) {
            log.warn(
                    "启动回收遗留处理中文档: recoveredCount={}",
                    recovered
            );
        } else {
            log.info("启动检查遗留处理中文档完成，无需回收");
        }
    }
}
