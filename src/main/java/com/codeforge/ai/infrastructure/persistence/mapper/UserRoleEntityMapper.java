package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.auth.entity.UserRoleEntity;
import com.mybatisflex.core.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRoleEntityMapper extends BaseMapper<UserRoleEntity> {

    @Select("SELECT id, user_id AS userId, role_code AS roleCode, "
            + "created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM user_role "
            + "WHERE user_id = #{userId} AND is_deleted = 0")
    List<UserRoleEntity> findByUserId(Long userId);

    @Select("<script>"
            + "SELECT id, user_id AS userId, role_code AS roleCode, "
            + "created_by AS createdBy, updated_by AS updatedBy, "
            + "created_at AS createdAt, updated_at AS updatedAt, is_deleted AS isDeleted "
            + "FROM user_role "
            + "WHERE is_deleted = 0 AND user_id IN "
            + "<foreach collection=\"userIds\" item=\"userId\" open=\"(\" separator=\",\" close=\")\">"
            + "#{userId}"
            + "</foreach>"
            + "</script>")
    List<UserRoleEntity> findByUserIds(List<Long> userIds);
}
