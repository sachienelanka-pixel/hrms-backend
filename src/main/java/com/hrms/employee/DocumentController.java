package com.hrms.employee;

import com.hrms.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @Autowired
    private S3Service s3Service;

    // Single file upload (profile photo, NIC, birth certificate, degree cert)
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type) {
        
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty or missing"));
        }

        // Validate PDF for document types (not for photos)
        if (!type.equalsIgnoreCase("profilePhoto")) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only PDF files are accepted for document uploads."));
            }
        }

        try {
            String folder = type != null ? type.toLowerCase().trim() : "others";
            String fileUrl = s3Service.uploadFile(file, folder);
            return ResponseEntity.ok(Map.of("url", fileUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "File upload failed: " + e.getMessage()));
        }
    }

    /**
     * Multi-file upload endpoint for educational qualifications and professional qualifications.
     * Accepts up to 5 PDF files per category.
     */
    @PostMapping("/upload-multiple")
    public ResponseEntity<?> uploadMultipleDocuments(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("type") String type) {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No files provided"));
        }

        int maxFiles = 5;
        if (files.size() > maxFiles) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Maximum " + maxFiles + " files allowed per category."));
        }

        // Validate all files are PDFs
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Only PDF files are accepted. File '" + originalFilename + "' is not a PDF."));
            }
        }

        try {
            String folder = type != null ? type.toLowerCase().trim() : "others";
            List<String> uploadedUrls = new ArrayList<>();
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String fileUrl = s3Service.uploadFile(file, folder);
                    uploadedUrls.add(fileUrl);
                }
            }
            return ResponseEntity.ok(Map.of("urls", uploadedUrls));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "File upload failed: " + e.getMessage()));
        }
    }
}
