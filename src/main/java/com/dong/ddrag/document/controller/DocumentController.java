package com.dong.ddrag.document.controller;

import com.dong.ddrag.common.api.ApiResponse;
import com.dong.ddrag.document.model.dto.DocumentQuery;
import com.dong.ddrag.document.model.dto.UploadDocumentRequest;
import com.dong.ddrag.document.model.vo.DocumentListItemVO;
import com.dong.ddrag.document.model.vo.DocumentPreviewVO;
import com.dong.ddrag.document.service.DocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
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

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
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

    @GetMapping("/{documentId}/preview")
    public DocumentPreviewVO previewDocument(
            @PathVariable Long documentId,
            @RequestParam Long groupId,
            HttpServletRequest request
    ) {
        return documentService.previewDocument(request, groupId, documentId);
    }
}
