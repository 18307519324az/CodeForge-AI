package com.codeforge.ai.infrastructure.persistence.mapper;

import com.codeforge.ai.domain.auth.entity.UserEntity;
import com.codeforge.ai.domain.auth.enums.UserStatus;
import com.codeforge.ai.domain.workspace.entity.WorkspaceEntity;
import com.codeforge.ai.domain.workspace.entity.WorkspaceMemberEntity;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberRole;
import com.codeforge.ai.domain.workspace.enums.WorkspaceMemberStatus;
import com.codeforge.ai.domain.workspace.enums.WorkspaceStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.session.store-type=none"
})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "mybatis-flex.configuration.map-underscore-to-camel-case=false"
})
class WorkspaceMemberMapperMapsSnakeCaseColumnsTest {

    private static final String PROBE_ACCOUNT = "mapper-probe-user";

    @Autowired
    private WorkspaceMemberEntityMapper workspaceMemberEntityMapper;

    @Autowired
    private WorkspaceEntityMapper workspaceEntityMapper;

    @Autowired
    private UserEntityMapper userEntityMapper;

    private long probeUserId;
    private long probeWorkspaceId;

    @BeforeEach
    void setUp() {
        probeUserId = ensureProbeUser();
        probeWorkspaceId = 88000L + probeUserId;
        ensureProbeWorkspace();
        ensureProbeMembership();
    }

    @Test
    void findByUserIdShouldMapSnakeCaseColumnsWithoutCamelCaseConfig() {
        List<WorkspaceMemberEntity> memberships = workspaceMemberEntityMapper.findByUserId(probeUserId);
        assertThat(memberships).hasSize(1);

        WorkspaceMemberEntity membership = memberships.get(0);
        assertThat(membership.getId()).isNotNull();
        assertThat(membership.getWorkspaceId()).isEqualTo(probeWorkspaceId);
        assertThat(membership.getUserId()).isEqualTo(probeUserId);
        assertThat(membership.getMemberRole()).isEqualTo(WorkspaceMemberRole.OWNER.name());
        assertThat(membership.getMemberStatus()).isEqualTo(WorkspaceMemberStatus.ACTIVE.name());
    }

    private long ensureProbeUser() {
        UserEntity existing = userEntityMapper.findByAccount(PROBE_ACCOUNT);
        if (existing != null) {
            return existing.getId();
        }
        UserEntity userEntity = UserEntity.builder()
                .account(PROBE_ACCOUNT)
                .passwordHash("hash")
                .displayName("Mapper Probe")
                .email("mapper-probe@example.com")
                .status(UserStatus.ACTIVE.name())
                .build();
        userEntity.setCreatedAt(LocalDateTime.now());
        userEntity.setUpdatedAt(LocalDateTime.now());
        userEntity.setIsDeleted(0);
        userEntityMapper.insertUser(userEntity);
        return userEntityMapper.findByAccount(PROBE_ACCOUNT).getId();
    }

    private void ensureProbeWorkspace() {
        if (workspaceEntityMapper.selectOneById(probeWorkspaceId) != null) {
            return;
        }
        WorkspaceEntity workspaceEntity = WorkspaceEntity.builder()
                .id(probeWorkspaceId)
                .name("Mapper Probe Workspace")
                .description("probe")
                .ownerUserId(probeUserId)
                .status(WorkspaceStatus.ACTIVE.name())
                .planCode("FREE")
                .build();
        workspaceEntity.setCreatedBy(probeUserId);
        workspaceEntity.setUpdatedBy(probeUserId);
        workspaceEntity.setCreatedAt(LocalDateTime.now());
        workspaceEntity.setUpdatedAt(LocalDateTime.now());
        workspaceEntity.setIsDeleted(0);
        workspaceEntityMapper.insert(workspaceEntity);
    }

    private void ensureProbeMembership() {
        if (workspaceMemberEntityMapper.findAnyByWorkspaceIdAndUserId(probeWorkspaceId, probeUserId) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        WorkspaceMemberEntity membership = WorkspaceMemberEntity.builder()
                .workspaceId(probeWorkspaceId)
                .userId(probeUserId)
                .memberRole(WorkspaceMemberRole.OWNER.name())
                .memberStatus(WorkspaceMemberStatus.ACTIVE.name())
                .invitedBy(probeUserId)
                .joinedAt(now)
                .build();
        membership.setCreatedBy(probeUserId);
        membership.setUpdatedBy(probeUserId);
        membership.setCreatedAt(now);
        membership.setUpdatedAt(now);
        membership.setIsDeleted(0);
        workspaceMemberEntityMapper.insertWorkspaceMember(membership);
    }
}
