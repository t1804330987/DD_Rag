package com.dong.ddrag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.dong.ddrag.ingestion.service.DocumentIngestionProcessor;

@SpringBootTest
class ContextLoadTest {

    @MockBean
    private DocumentIngestionProcessor documentIngestionProcessor;

    @Test
    void contextLoads() {
    }
}
