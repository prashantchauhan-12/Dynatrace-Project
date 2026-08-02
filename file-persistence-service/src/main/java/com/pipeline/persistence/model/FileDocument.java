package com.pipeline.persistence.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ╔══════════════════════════════════════════════════════════════╗
 * ║  FILE DOCUMENT — JPA Entity                                  ║
 * ║                                                              ║
 * ║  This is the database table where transformed files are      ║
 * ║  stored. Hibernate auto-creates this table from the entity.  ║
 * ║                                                              ║
 * ║  Table name: file_documents                                  ║
 * ╚══════════════════════════════════════════════════════════════╝
 */
@Entity
@Table(name = "file_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_id", nullable = false, unique = true)
    private String fileId;              // Correlation ID from S1

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "title", nullable = false)
    private String title;               // Parsed from [TITLE]

    @Column(name = "logo_url")
    private String logoUrl;             // Parsed from [LOGO]

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;             // Parsed from [CONTENT]

    @Column(name = "footer")
    private String footer;              // Parsed from [FOOTER]

    @Column(name = "status")
    private String status;              // "SUCCESS" or "FAILED"

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
