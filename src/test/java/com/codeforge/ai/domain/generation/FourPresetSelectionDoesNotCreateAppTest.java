package com.codeforge.ai.domain.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FourPresetSelectionDoesNotCreateAppTest {

    @Test
    void presetSelectionOnlyUpdatesContextFields() {
        WorkspacePresetSelection selection = WorkspacePresetSelection.fromPreset(
                "crm",
                "客户管理后台",
                "生成一个客户管理后台，包含客户列表、客户详情和客户编辑表单。",
                "ADMIN_WEB");

        assertThat(selection.selectedPresetKey()).isEqualTo("crm");
        assertThat(selection.requirement()).contains("客户列表");
        assertThat(selection.appType()).isEqualTo("ADMIN_WEB");
        assertThat(selection.createsApp()).isFalse();
        assertThat(selection.submitsTask()).isFalse();
    }

    record WorkspacePresetSelection(
            String selectedPresetKey,
            String requirement,
            String appType,
            boolean createsApp,
            boolean submitsTask) {

        static WorkspacePresetSelection fromPreset(
                String key,
                String label,
                String prompt,
                String appType) {
            return new WorkspacePresetSelection(key, prompt, appType, false, false);
        }
    }
}
