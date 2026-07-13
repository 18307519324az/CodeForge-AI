package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.model.entity.ModelProviderEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ModelProviderEntityMapper extends BaseMapper<ModelProviderEntity> {

    // Legacy: used by AdminQueryApplicationService
    @Select("SELECT id, provider_code AS providerCode, provider_name AS providerName, "
            + "base_url AS baseUrl, auth_mode AS authMode, api_protocol AS apiProtocol, secret_ref AS secretRef, "
            + "api_key_env AS apiKeyEnv, credential_source AS credentialSource, default_model AS defaultModel, priority, status, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM model_provider WHERE is_deleted = 0 ORDER BY priority ASC, created_at DESC")
    List<ModelProviderEntity> findAllProviders();

    @Select("SELECT id, provider_code AS providerCode, provider_name AS providerName, "
            + "base_url AS baseUrl, auth_mode AS authMode, api_protocol AS apiProtocol, secret_ref AS secretRef, status, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM model_provider WHERE status = 'ACTIVE' AND is_deleted = 0 ORDER BY id ASC LIMIT 1")
    ModelProviderEntity findFirstActiveProvider();

    // New: with AS aliases
    @Select("SELECT id, provider_code AS providerCode, provider_name AS providerName, "
            + "base_url AS baseUrl, auth_mode AS authMode, api_protocol AS apiProtocol, secret_ref AS secretRef, "
            + "api_key_env AS apiKeyEnv, credential_source AS credentialSource, default_model AS defaultModel, priority, status, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM model_provider WHERE is_deleted = 0 ORDER BY priority ASC")
    List<ModelProviderEntity> findAll();

    @Select("SELECT id, provider_code AS providerCode, provider_name AS providerName, "
            + "base_url AS baseUrl, auth_mode AS authMode, api_protocol AS apiProtocol, secret_ref AS secretRef, "
            + "api_key_env AS apiKeyEnv, credential_source AS credentialSource, default_model AS defaultModel, priority, status, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM model_provider WHERE id = #{id} AND is_deleted = 0 LIMIT 1")
    ModelProviderEntity findById(@Param("id") Long id);

    @Update("UPDATE model_provider SET status = #{status}, updated_by = #{updatedBy} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("updatedBy") Long updatedBy);

    // Legacy: used by AdminModelProviderApplicationService
    @Update("UPDATE model_provider SET provider_name = #{providerName}, base_url = #{baseUrl}, "
            + "auth_mode = #{authMode}, secret_ref = #{secretRef}, status = #{status}, "
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt} WHERE id = #{id} AND is_deleted = 0")
    int updateProvider(ModelProviderEntity entity);

    @Update("UPDATE model_provider SET provider_name = #{providerName}, base_url = #{baseUrl}, "
            + "auth_mode = #{authMode}, default_model = #{defaultModel}, priority = #{priority}, "
            + "credential_source = #{credentialSource}, status = #{status}, "
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt} WHERE id = #{id} AND is_deleted = 0")
    int updateProviderAdmin(ModelProviderEntity entity);

    @Update("UPDATE model_provider SET is_deleted = 1, status = 'DISABLED', updated_by = #{updatedBy}, "
            + "updated_at = #{updatedAt} WHERE provider_code = #{providerCode} AND is_deleted = 0")
    int softDeleteByProviderCode(@Param("providerCode") String providerCode,
                                 @Param("updatedBy") Long updatedBy,
                                 @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Select("SELECT id, provider_code AS providerCode, provider_name AS providerName, "
            + "base_url AS baseUrl, auth_mode AS authMode, api_protocol AS apiProtocol, secret_ref AS secretRef, "
            + "api_key_env AS apiKeyEnv, credential_source AS credentialSource, default_model AS defaultModel, priority, status, "
            + "created_by AS createdBy, updated_by AS updatedBy, created_at AS createdAt, "
            + "updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM model_provider WHERE provider_code = #{code} AND is_deleted = 0 LIMIT 1")
    ModelProviderEntity findByProviderCode(@Param("code") String code);

    @org.apache.ibatis.annotations.Insert("INSERT INTO model_provider (provider_code, provider_name, base_url, auth_mode, "
            + "api_protocol, api_key_env, credential_source, default_model, priority, status, created_by, updated_by, created_at, updated_at, is_deleted) "
            + "VALUES (#{providerCode}, #{providerName}, #{baseUrl}, #{authMode}, #{apiProtocol}, #{apiKeyEnv}, "
            + "#{credentialSource}, #{defaultModel}, #{priority}, #{status}, #{createdBy}, #{updatedBy}, #{createdAt}, #{updatedAt}, #{isDeleted})")
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProvider(ModelProviderEntity entity);

    @Update("UPDATE model_provider SET base_url = #{baseUrl}, default_model = #{defaultModel}, api_key_env = #{apiKeyEnv}, "
            + "updated_by = #{updatedBy}, updated_at = #{updatedAt} WHERE id = #{id} AND is_deleted = 0")
    int updateAiCatalog(ModelProviderEntity entity);
}
