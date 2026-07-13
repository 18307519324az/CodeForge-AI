package com.codeforge.ai.application.generation;

/**
 * Fixed regression requirements for generation output budget gates.
 */
public final class RegressionGenerationRequirements {

    public static final String MINIMAL_TODO =
            "生成一个极简待办事项页面，包含输入框、添加按钮、待办列表和完成状态切换";

    public static final String SIMPLE_BLOG =
            "生成一个简洁个人博客首页，包含文章列表和分类筛选";

    public static final String STANDARD_CRM =
            "生成客户管理后台，包含客户列表、客户详情和客户编辑表单";

    public static final String ECOMMERCE =
            "生成在线商城后台，包含商品管理、订单管理和用户管理";

    private RegressionGenerationRequirements() {
    }
}
