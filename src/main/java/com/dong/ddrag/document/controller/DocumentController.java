package com.dong.ddrag.document.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.document.model.dto.DocumentQuery;
import com.dong.ddrag.document.model.dto.UploadDocumentRequest;
import com.dong.ddrag.document.model.dto.UploadInitRequest;
import com.dong.ddrag.document.model.dto.UploadChunkRequest;
import com.dong.ddrag.document.model.vo.DocumentListItemVO;
import com.dong.ddrag.document.model.vo.DocumentPreviewVO;
import com.dong.ddrag.document.model.vo.UploadInitResponse;
import com.dong.ddrag.document.model.vo.UploadStatusResponse;
import com.dong.ddrag.document.service.DocumentService;
import com.dong.ddrag.document.service.DocumentUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentUploadService documentUploadService;

    public DocumentController(DocumentService documentService, DocumentUploadService documentUploadService) {
        this.documentService = documentService;
        this.documentUploadService = documentUploadService;
    }

    @PostMapping(path = "/upload/init", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<UploadInitResponse> initUpload(
            @RequestBody UploadInitRequest uploadRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(documentUploadService.initUpload(request, uploadRequest));
    }

    @PostMapping(path = "/upload/chunks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadStatusResponse> uploadChunk(
            @ModelAttribute UploadChunkRequest uploadRequest,
            HttpServletRequest request
    ) {
        documentUploadService.uploadChunk(request, uploadRequest);
        return ApiResponse.success(documentUploadService.getUploadStatus(request, uploadRequest.uploadId()));
    }

    @GetMapping("/upload/{uploadId}")
    public ApiResponse<UploadStatusResponse> getUploadStatus(
            @PathVariable String uploadId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(documentUploadService.getUploadStatus(request, uploadId));
    }

    @PostMapping("/upload/{uploadId}/complete")
    public ApiResponse<Long> completeUpload(
            @PathVariable String uploadId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(documentUploadService.completeUpload(request, uploadId));
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> uploadDocument(
            @ModelAttribute UploadDocumentRequest uploadRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(documentService.uploadDocument(request, uploadRequest));
    }

    @GetMapping
    public List<DocumentListItemVO> listDocuments(
            @ModelAttribute DocumentQuery query,
            HttpServletRequest request
    ) {
        return documentService.listDocuments(request, query);
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @PathVariable Long documentId,
            @RequestParam Long groupId,
            HttpServletRequest request
    ) {
        documentService.softDeleteDocument(request, groupId, documentId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{documentId}/retry-ingestion")
    public ApiResponse<Void> retryDocumentIngestion(
            @PathVariable Long documentId,
            @RequestParam Long groupId,
            HttpServletRequest request
    ) {
        documentService.retryFailedDocumentIngestion(request, groupId, documentId);
        return ApiResponse.success(null);
    }

    @GetMapping("/{documentId}/preview")
    public DocumentPreviewVO previewDocument(
            @PathVariable Long documentId,
            @RequestParam Long groupId,
            HttpServletRequest request
    ) {
        return documentService.previewDocument(request, groupId, documentId);
    }
}
