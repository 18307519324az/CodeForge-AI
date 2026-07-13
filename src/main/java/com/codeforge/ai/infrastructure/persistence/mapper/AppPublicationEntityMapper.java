package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.app.entity.AppPublicationEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppPublicationEntityMapper extends BaseMapper<AppPublicationEntity> {

    String PUBLICATION_COLUMNS =
            "id, app_id AS appId, version_id AS versionId, publisher_user_id AS publisherUserId, "
                    + "public_title AS publicTitle, public_description AS publicDescription, slug, status, "
                    + "allow_preview AS allowPreview, allow_download AS allowDownload, "
                    + "published_at AS publishedAt, unpublished_at AS unpublishedAt, "
                    + "view_count AS viewCount, download_count AS downloadCount, "
                    + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
                    + "updated_at AS updatedAt, is_deleted AS isDeleted";

    @Select("SELECT " + PUBLICATION_COLUMNS + " FROM app_publication WHERE app_id = #{appId} AND is_deleted = 0 LIMIT 1")
    AppPublicationEntity findByAppId(@Param("appId") Long appId);

    @Select("<script>"
            + "SELECT " + PUBLICATION_COLUMNS + " FROM app_publication "
            + "WHERE is_deleted = 0 AND app_id IN "
            + "<foreach collection='appIds' item='appId' open='(' separator=',' close=')'>"
            + "#{appId}"
            + "</foreach>"
            + "</script>")
    List<AppPublicationEntity> findByAppIds(@Param("appIds") List<Long> appIds);

    @Select("SELECT " + PUBLICATION_COLUMNS + " FROM app_publication WHERE slug = #{slug} AND is_deleted = 0 LIMIT 1")
    AppPublicationEntity findBySlug(@Param("slug") String slug);

    @Select("SELECT " + PUBLICATION_COLUMNS + " FROM app_publication WHERE id = #{id} AND is_deleted = 0 LIMIT 1")
    AppPublicationEntity findActiveById(@Param("id") Long id);

    @Select("<script>"
            + "SELECT " + PUBLICATION_COLUMNS + " FROM app_publication "
            + "WHERE is_deleted = 0 AND status = 'PUBLISHED' "
            + "AND app_id IN (SELECT id FROM ai_app WHERE is_deleted = 0 AND status != 'ARCHIVED') "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (public_title LIKE CONCAT('%', #{keyword}, '%') OR public_description LIKE CONCAT('%', #{keyword}, '%')) "
            + "</if>"
            + "<if test='appType != null and appType != \"\"'>"
            + "AND app_id IN (SELECT id FROM ai_app WHERE is_deleted = 0 AND app_type = #{appType}) "
            + "</if>"
            + "ORDER BY "
            + "<choose>"
            + "<when test=\"sort == 'POPULAR'\">view_count DESC, published_at DESC, id DESC</when>"
            + "<otherwise>published_at DESC, id DESC</otherwise>"
            + "</choose> "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<AppPublicationEntity> findPublishedPage(@Param("keyword") String keyword,
                                                 @Param("appType") String appType,
                                                 @Param("sort") String sort,
                                                 @Param("limit") long limit,
                                                 @Param("offset") long offset);

    @Select("<script>"
            + "SELECT COUNT(1) FROM app_publication "
            + "WHERE is_deleted = 0 AND status = 'PUBLISHED' "
            + "AND app_id IN (SELECT id FROM ai_app WHERE is_deleted = 0 AND status != 'ARCHIVED') "
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (public_title LIKE CONCAT('%', #{keyword}, '%') OR public_description LIKE CONCAT('%', #{keyword}, '%')) "
            + "</if>"
            + "<if test='appType != null and appType != \"\"'>"
            + "AND app_id IN (SELECT id FROM ai_app WHERE is_deleted = 0 AND app_type = #{appType}) "
            + "</if>"
            + "</script>")
    long countPublished(@Param("keyword") String keyword, @Param("appType") String appType);

    @Update("UPDATE app_publication SET view_count = view_count + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND is_deleted = 0")
    int incrementViewCount(@Param("id") Long id);

    @Update("UPDATE app_publication SET download_count = download_count + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND is_deleted = 0")
    int incrementDownloadCount(@Param("id") Long id);
}
