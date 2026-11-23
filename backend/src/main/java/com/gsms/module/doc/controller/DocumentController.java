package com.gsms.module.doc.controller;

import com.gsms.common.R;
import com.gsms.module.doc.dto.DocumentVO;
import com.gsms.module.doc.service.DocumentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/cases/{caseId}/documents")
    @PreAuthorize("hasAuthority('doc:create')")
    public R<Long> upload(@PathVariable Long caseId,
                          @RequestParam("file") MultipartFile file,
                          @RequestParam(value = "docType", required = false) String docType,
                          @RequestParam(value = "sensitiveFlag", required = false) Integer sensitiveFlag) {
        return R.ok(documentService.upload(caseId, file, docType, sensitiveFlag));
    }

    @GetMapping("/cases/{caseId}/documents")
    @PreAuthorize("hasAuthority('doc:view')")
    public R<List<DocumentVO>> list(@PathVariable Long caseId) {
        return R.ok(documentService.list(caseId));
    }

    @PostMapping("/documents/{id}/versions")
    @PreAuthorize("hasAuthority('doc:create')")
    public R<Long> newVersion(@PathVariable Long id,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(value = "note", required = false) String note) {
        return R.ok(documentService.newVersion(id, file, note));
    }

    @GetMapping("/documents/{id}/versions")
    @PreAuthorize("hasAuthority('doc:view')")
    public R<List<DocumentVO>> versions(@PathVariable Long id) {
        return R.ok(documentService.versions(id));
    }

    @GetMapping("/documents/{id}/preview")
    @PreAuthorize("hasAuthority('doc:view')")
    public ResponseEntity<?> preview(@PathVariable Long id) {
        return build(documentService.download(id, true), true);
    }

    @GetMapping("/documents/{id}/download")
    @PreAuthorize("hasAuthority('doc:view')")
    public ResponseEntity<?> download(@PathVariable Long id) {
        return build(documentService.download(id, false), false);
    }

    @DeleteMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('doc:delete')")
    public R<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return R.ok();
    }

    private ResponseEntity<Resource> build(DocumentService.DownloadResult result, boolean inline) {
        String encoded = result.getFileName();
        try {
            encoded = URLEncoder.encode(result.getFileName(), "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException ignored) {
            // UTF-8 必然支持，不会走到这里
        }
        String disposition = (inline ? "inline" : "attachment")
                + "; filename*=UTF-8''" + encoded;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(result.getContentType()));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
        headers.setCacheControl("no-store");

        // 直接流式输出磁盘文件，避免整文件读入堆内存
        return new ResponseEntity<>(new FileSystemResource(result.getPath()), headers,
                org.springframework.http.HttpStatus.OK);
    }
}
