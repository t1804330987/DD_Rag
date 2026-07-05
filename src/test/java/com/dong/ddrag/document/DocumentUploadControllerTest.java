package com.dong.ddrag.document;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.common.exception.GlobalExceptionHandler;
import com.dong.ddrag.document.controller.DocumentController;
import com.dong.ddrag.document.model.dto.UploadChunkRequest;
import com.dong.ddrag.document.model.dto.UploadInitRequest;
import com.dong.ddrag.document.model.vo.UploadInitResponse;
import com.dong.ddrag.document.model.vo.UploadStatusResponse;
import com.dong.ddrag.document.service.DocumentService;
import com.dong.ddrag.document.service.DocumentUploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
class DocumentUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private DocumentUploadService documentUploadService;

    @MockBean
    private JwtAccessTokenService jwtAccessTokenService;

    @Test
    void shouldInitUploadSession() throws Exception {
        UploadInitRequest request = new UploadInitRequest(
                2001L,
                "产品需求文档.txt",
                1024L,
                "text/plain",
                "hash-abc",
                512L,
                2
        );
        given(documentUploadService.initUpload(any(), any()))
                .willReturn(UploadInitResponse.uploadSession("upload-001", 512L, 2));

        mockMvc.perform(post("/api/documents/upload/init")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadId").value("upload-001"))
                .andExpect(jsonPath("$.data.instantUpload").value(false));
    }

    @Test
    void shouldUploadChunk() throws Exception {
        MockMultipartFile chunk = new MockMultipartFile(
                "chunk",
                "chunk-0.part",
                "application/octet-stream",
                "chunk-body".getBytes()
        );
        given(documentUploadService.uploadChunk(any(), any()))
                .willReturn(List.of(0, 1));
        given(documentUploadService.getUploadStatus(any(), any()))
                .willReturn(new UploadStatusResponse("UPLOADING", List.of(0, 1), 2, 3));

        mockMvc.perform(multipart("/api/documents/upload/chunks")
                        .file(chunk)
                        .param("uploadId", "upload-001")
                        .param("chunkIndex", "1")
                        .param("chunkHash", "chunk-hash-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                .andExpect(jsonPath("$.data.uploadedChunkCount").value(2))
                .andExpect(jsonPath("$.data.chunkCount").value(3))
                .andExpect(jsonPath("$.data.uploadedChunks[0]").value(0))
                .andExpect(jsonPath("$.data.uploadedChunks[1]").value(1));
    }

    @Test
    void shouldReturnUploadStatus() throws Exception {
        given(documentUploadService.getUploadStatus(any(), any()))
                .willReturn(new UploadStatusResponse("UPLOADING", List.of(0, 1), 2, 3));

        mockMvc.perform(get("/api/documents/upload/upload-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                .andExpect(jsonPath("$.data.uploadedChunkCount").value(2))
                .andExpect(jsonPath("$.data.chunkCount").value(3));
    }

    @Test
    void shouldCompleteUpload() throws Exception {
        given(documentUploadService.completeUpload(any(), any()))
                .willReturn(4001L);

        mockMvc.perform(post("/api/documents/upload/upload-001/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(4001L));
    }

    @Test
    void shouldRetryFailedIngestion() throws Exception {
        mockMvc.perform(post("/api/documents/4001/retry-ingestion")
                        .param("groupId", "2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(documentService).should().retryFailedDocumentIngestion(any(), org.mockito.ArgumentMatchers.eq(2001L), org.mockito.ArgumentMatchers.eq(4001L));
    }
}
