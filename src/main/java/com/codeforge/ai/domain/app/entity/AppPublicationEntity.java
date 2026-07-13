package com.codeforge.ai.domain.app.entity;

import com.codeforge.ai.domain.common.BaseEntity;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("app_publication")
public class AppPublicationEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;

    private Long appId;
    private Long versionId;
    private Long publisherUserId;
    private String publicTitle;
    private String publicDescription;
    private String slug;
    private String status;
    private Boolean allowPreview;
    private Boolean allowDownload;
    private LocalDateTime publishedAt;
    private LocalDateTime unpublishedAt;
    private Long viewCount;
    private Long downloadCount;
}
