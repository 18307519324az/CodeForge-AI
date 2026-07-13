package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.mybatisflex.core.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserEntityMapper extends BaseMapper<UserEntity> {

    @Select("SELECT id, account, password_hash AS passwordHash, display_name AS displayName, "
            + "avatar_url AS avatarUrl, email, phone, status, "
            + "last_login_at AS lastLoginAt, created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM user "
            + "WHERE account = #{account} AND is_deleted = 0 LIMIT 1")
    UserEntity findByAccount(String account);

    @Select("SELECT id, account, password_hash AS passwordHash, display_name AS displayName, "
            + "avatar_url AS avatarUrl, email, phone, status, "
            + "last_login_at AS lastLoginAt, created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM user "
            + "WHERE email = #{email} AND is_deleted = 0 LIMIT 1")
    UserEntity findByEmail(String email);

    @Select("SELECT id, account, password_hash AS passwordHash, display_name AS displayName, "
            + "avatar_url AS avatarUrl, email, phone, status, "
            + "last_login_at AS lastLoginAt, created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM user "
            + "WHERE id = #{userId} AND is_deleted = 0 LIMIT 1")
    UserEntity findById(Long userId);

    @Insert("INSERT INTO user (account, password_hash, display_name, avatar_url, email, phone, status, created_at, updated_at) "
            + "VALUES (#{account}, #{passwordHash}, #{displayName}, #{avatarUrl}, #{email}, #{phone}, #{status}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(UserEntity entity);

    @Select("<script>"
            + "SELECT id, account, password_hash AS passwordHash, display_name AS displayName, "
            + "avatar_url AS avatarUrl, email, phone, status, "
            + "last_login_at AS lastLoginAt, created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM user "
            + "WHERE is_deleted = 0 AND id IN "
            + "<foreach collection=\"userIds\" item=\"userId\" open=\"(\" separator=\",\" close=\")\">"
            + "#{userId}"
            + "</foreach>"
            + "</script>")
    List<UserEntity> findByIds(List<Long> userIds);

    @Select("<script>"
            + "SELECT id, account, password_hash AS passwordHash, display_name AS displayName, "
            + "avatar_url AS avatarUrl, email, phone, status, "
            + "last_login_at AS lastLoginAt, created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM user "
            + "WHERE is_deleted = 0 "
            + "<if test=\"keywordPattern != null and keywordPattern != ''\">"
            + "  AND (account LIKE #{keywordPattern} OR display_name LIKE #{keywordPattern} OR email LIKE #{keywordPattern})"
            + "</if>"
            + "ORDER BY created_at DESC, id DESC "
            + "LIMIT #{limit} OFFSET #{offset}"
            + "</script>")
    List<UserEntity> findPage(@Param("offset") long offset,
                              @Param("limit") long limit,
                              @Param("keywordPattern") String keywordPattern);

    @Select("SELECT COUNT(1) FROM user WHERE is_deleted = 0")
    long countAllUsers();

    @Select("<script>"
            + "SELECT COUNT(1) FROM user WHERE is_deleted = 0 "
            + "<if test=\"keywordPattern != null and keywordPattern != ''\">"
            + "  AND (account LIKE #{keywordPattern} OR display_name LIKE #{keywordPattern} OR email LIKE #{keywordPattern})"
            + "</if>"
            + "</script>")
    long countByKeyword(@Param("keywordPattern") String keywordPattern);

    @Update("UPDATE user SET last_login_at = #{lastLoginAt}, updated_by = #{updatedBy} "
            + "WHERE id = #{userId} AND is_deleted = 0")
    int updateLastLogin(Long userId, LocalDateTime lastLoginAt, Long updatedBy);

    @Update("<script>"
            + "UPDATE user <set>"
            + "<if test=\"displayName != null\">display_name = #{displayName},</if>"
            + "<if test=\"avatarUrl != null\">avatar_url = #{avatarUrl},</if>"
            + "<if test=\"email != null\">email = #{email},</if>"
            + "<if test=\"phone != null\">phone = #{phone},</if>"
            + "updated_by = #{updatedBy}"
            + "</set>"
            + "WHERE id = #{id} AND is_deleted = 0"
            + "</script>")
    int updateProfile(UserEntity entity);
}
