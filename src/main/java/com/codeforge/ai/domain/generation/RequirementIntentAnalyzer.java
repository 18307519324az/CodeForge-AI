package com.codeforge.ai.domain.generation;

import org.springframework.stereotype.Component;

@Component
public class RequirementIntentAnalyzer {

    public BusinessIntent classify(String requirement) {
        String value = normalize(requirement);
        if (value.contains("待办") || value.contains("todo")) {
            return BusinessIntent.TODO_LIST;
        }
        if (value.contains("商城") || value.contains("商品") || value.contains("订单") || value.contains("电商")) {
            return BusinessIntent.ECOMMERCE_ADMIN;
        }
        if (value.contains("客户") || value.contains("crm")) {
            return BusinessIntent.CUSTOMER_CRM;
        }
        if (value.contains("工单") || value.contains("ticket") || value.contains("客服")) {
            return BusinessIntent.TICKET_SYSTEM;
        }
        if (value.contains("博客") || value.contains("文章")) {
            return BusinessIntent.BLOG;
        }
        if (value.contains("看板") || value.contains("仪表盘") || value.contains("dashboard") || value.contains("分析")) {
            return BusinessIntent.DASHBOARD;
        }
        if (value.contains("项目") || value.contains("迭代") || value.contains("任务管理")) {
            return BusinessIntent.PROJECT_MANAGEMENT;
        }
        if (value.contains("订单")) {
            return BusinessIntent.ORDER_MANAGEMENT;
        }
        return BusinessIntent.GENERIC_WEB_APP;
    }

    public String deriveAppName(String requirement) {
        BusinessIntent intent = classify(requirement);
        return switch (intent) {
            case TODO_LIST -> "待办清单";
            case ECOMMERCE_ADMIN -> "商城后台管理系统";
            case CUSTOMER_CRM -> "客户管理后台";
            case ORDER_MANAGEMENT -> "订单管理系统";
            case TICKET_SYSTEM -> "工单管理系统";
            case BLOG -> "内容发布平台";
            case DASHBOARD -> "数据分析看板";
            case PROJECT_MANAGEMENT -> "项目管理平台";
            case GENERIC_WEB_APP -> deriveGenericName(requirement);
        };
    }

    public String deriveDescription(String requirement) {
        String normalized = normalize(requirement)
                .replaceFirst("^生成(一个|一套|一页)?", "")
                .replaceFirst("^创建(一个|一套|一页)?", "")
                .trim();
        if (normalized.isEmpty()) {
            return "根据需求生成的应用原型";
        }
        return normalized;
    }

    private String deriveGenericName(String requirement) {
        String normalized = normalize(requirement)
                .replaceFirst("^生成(一个|一套|一页)?", "")
                .replaceFirst("^创建(一个|一套|一页)?", "")
                .replace("页面", "")
                .replace("系统", "系统")
                .replace("后台", "后台")
                .trim();
        if (normalized.length() >= 4 && normalized.length() <= 16) {
            return normalized;
        }
        if (normalized.length() > 16) {
            return normalized.substring(0, 16);
        }
        return "未命名应用";
    }

    private String normalize(String requirement) {
        return requirement == null ? "" : requirement.replace('，', ' ').replace('。', ' ').trim();
    }
}
