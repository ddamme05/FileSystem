package org.ddamme.database.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "file_metadata")
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Helper method to get user ID without loading lazy relationship
    public Long getUserId() {
        return user != null ? user.getId() : null;
    }

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false, unique = true)
    private String storageKey;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false)
    private String contentType;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant uploadTimestamp;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updateTimestamp;

    // AI-related fields (Phase 1+)
    // Using basic String/primitive types to avoid Hibernate 6 PostgreSQL type mapping issues in tests
    @Column(name = "file_text", columnDefinition = "TEXT")
    private String fileText;

    // JSONB stored as String - will be serialized/deserialized manually if needed
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_structured_json")
    private String fileStructuredJson;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    // PostgreSQL TEXT[] array with Hibernate 6 native support
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "ai_keywords")
    @Array(length = 100)  // Maximum array length hint for schema generation
    private String[] aiKeywords;

    @Column(name = "pii_level", length = 20)
    private String piiLevel;

    @Column(name = "ocr_confidence")
    private Float ocrConfidence;

    @Column(name = "ocr_model_version", length = 50)
    private String ocrModelVersion;

    @Column(name = "embedding_model_version", length = 50)
    private String embeddingModelVersion;

    // Note: search_vector is a generated column in PostgreSQL, not mapped in JPA

    // Helper methods for list-based access (convenience methods)
    @Transient
    public List<String> getAiKeywordsList() {
        return aiKeywords != null ? Arrays.asList(aiKeywords) : null;
    }

    public void setAiKeywordsList(List<String> keywords) {
        this.aiKeywords = keywords != null ? keywords.toArray(new String[0]) : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FileMetadata that = (FileMetadata) obj;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FileMetadata{"
                + "id="
                + id
                + ", originalFilename='"
                + originalFilename
                + '\''
                + ", storageKey='"
                + storageKey
                + '\''
                + ", size="
                + size
                + ", contentType='"
                + contentType
                + '\''
                + '}';
    }
}

