package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.model.entity.ModelProviderCredentialEntity;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ModelProviderCredentialEntityMapper extends BaseMapper<ModelProviderCredentialEntity> {

    @Select("SELECT id, provider_id AS providerId, credential_type AS credentialType, "
            + "ciphertext, nonce, key_version AS keyVersion, masked_hint AS maskedHint, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM model_provider_credential "
            + "WHERE provider_id = #{providerId} AND is_deleted = 0 LIMIT 1")
    ModelProviderCredentialEntity findActiveByProviderId(@Param("providerId") Long providerId);

    @Select("SELECT id, provider_id AS providerId, credential_type AS credentialType, "
            + "ciphertext, nonce, key_version AS keyVersion, masked_hint AS maskedHint, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM model_provider_credential "
            + "WHERE provider_id = #{providerId} LIMIT 1")
    ModelProviderCredentialEntity findByProviderId(@Param("providerId") Long providerId);

    @Insert("INSERT INTO model_provider_credential (provider_id, credential_type, ciphertext, nonce, "
            + "key_version, masked_hint, created_at, updated_at, is_deleted) "
            + "VALUES (#{providerId}, #{credentialType}, #{ciphertext}, #{nonce}, "
            + "#{keyVersion}, #{maskedHint}, #{createdAt}, #{updatedAt}, #{isDeleted})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCredential(ModelProviderCredentialEntity entity);

    @Update("UPDATE model_provider_credential SET credential_type = #{credentialType}, ciphertext = #{ciphertext}, "
            + "nonce = #{nonce}, key_version = #{keyVersion}, masked_hint = #{maskedHint}, "
            + "updated_at = #{updatedAt}, is_deleted = 0 "
            + "WHERE provider_id = #{providerId}")
    int upsertByProviderId(ModelProviderCredentialEntity entity);

    @Update("UPDATE model_provider_credential SET is_deleted = 1, updated_at = #{updatedAt} "
            + "WHERE provider_id = #{providerId} AND is_deleted = 0")
    int softDeleteByProviderId(@Param("providerId") Long providerId, @Param("updatedAt") java.time.LocalDateTime updatedAt);
}
