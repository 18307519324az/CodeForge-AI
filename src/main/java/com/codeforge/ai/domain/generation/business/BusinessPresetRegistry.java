package com.codeforge.ai.domain.generation.business;

import com.codeforge.ai.domain.generation.BusinessIntent;
import com.codeforge.ai.domain.generation.RequirementIntentAnalyzer;
import java.util.List;
import java.util.Locale;

public final class BusinessPresetRegistry {

    private static final BusinessPreset TODO = new BusinessPreset(
            "TODO",
            List.of("WEB_APP", "KANBAN"),
            List.of("今日任务", "全部任务", "未来计划", "标签管理", "提醒中心", "完成率统计"),
            List.of("任务", "分类", "标签", "提醒"),
            List.of("Capture task", "Prioritize", "Schedule reminder", "Complete", "Review completion rate"),
            List.of("任务标题", "优先级", "截止时间", "完成状态", "标签", "提醒时间"),
            List.of("新增任务", "完成任务", "删除任务", "筛选任务", "拖拽排序"),
            List.of("完成率", "今日任务数量"),
            List.of("搜索", "过滤", "拖拽", "批量完成", "状态切换"),
            List.of("单一表格", "仅展示标题", "通用后台模板"));

    private static final BusinessPreset ECOMMERCE = new BusinessPreset(
            "ECOMMERCE",
            List.of("ADMIN", "ECOMMERCE", "DASHBOARD"),
            List.of("商品管理", "订单管理", "库存管理", "分类管理", "统计面板", "用户管理"),
            List.of("商品", "订单", "库存", "分类", "用户"),
            List.of("Product onboarding", "Inventory check", "Order fulfillment", "After-sales", "Revenue review"),
            List.of("商品名称", "价格", "库存", "分类", "订单状态", "购买用户"),
            List.of("新增商品", "编辑商品", "上下架", "批量操作", "订单处理"),
            List.of("销售额", "库存预警", "订单趋势"),
            List.of("搜索", "分页", "分类筛选", "批量上下架", "状态过滤"),
            List.of("只有商品表格", "忽略订单", "忽略库存", "通用后台模板"));

    private static final BusinessPreset CRM = new BusinessPreset(
            "CRM",
            List.of("CRM", "ADMIN", "DASHBOARD"),
            List.of("客户列表", "联系人", "跟进记录", "标签管理", "商机管理", "销售漏斗", "客户详情"),
            List.of("客户", "联系人", "跟进", "商机", "合同"),
            List.of("Lead capture", "Customer follow-up", "Opportunity", "Contract", "Closed won"),
            List.of("客户姓名", "等级", "联系方式", "跟进状态", "最近联系时间", "备注"),
            List.of("新增客户", "编辑客户", "记录跟进", "分配销售", "推进商机"),
            List.of("销售漏斗", "客户等级分布", "跟进转化率"),
            List.of("搜索", "等级过滤", "跟进状态过滤", "详情查看", "编辑表单"),
            List.of("只有客户表格", "忽略跟进", "忽略商机", "通用后台模板"));

    private static final BusinessPreset TICKET = new BusinessPreset(
            "TICKET",
            List.of("ADMIN", "KANBAN", "DASHBOARD"),
            List.of("工单列表", "状态流转", "评论协作", "处理记录", "附件管理", "负责人管理", "SLA 统计"),
            List.of("工单", "提交人", "处理人", "评论", "附件", "SLA"),
            List.of("Ticket submitted", "Triage", "Assign owner", "Resolve", "Review SLA"),
            List.of("工单编号", "提交人", "优先级", "处理人", "状态", "SLA", "问题描述"),
            List.of("提交工单", "分配处理人", "更新状态", "添加评论", "上传附件"),
            List.of("SLA 达成率", "待处理数量", "优先级分布"),
            List.of("搜索", "状态过滤", "优先级过滤", "看板流转", "评论时间线"),
            List.of("只有工单表格", "忽略状态流转", "忽略评论附件", "通用后台模板"));

    private static final BusinessPreset DASHBOARD = new BusinessPreset(
            "DASHBOARD",
            List.of("DASHBOARD"),
            List.of("指标总览", "趋势分析", "异常监控", "维度筛选", "明细表格"),
            List.of("指标", "趋势", "维度", "告警"),
            List.of("Collect metrics", "Compare trend", "Filter dimension", "Find anomaly", "Export insight"),
            List.of("指标名称", "当前值", "环比", "同比", "维度", "时间范围"),
            List.of("刷新", "导出", "切换维度", "查看明细"),
            List.of("趋势图", "占比图", "指标卡"),
            List.of("搜索", "时间过滤", "维度过滤", "钻取"),
            List.of("只有静态卡片", "缺少图表", "缺少过滤"));

    private static final BusinessPreset PORTAL = new BusinessPreset(
            "PORTAL",
            List.of("PORTAL", "WEB_APP"),
            List.of("入口导航", "内容推荐", "公告中心", "快捷操作", "数据摘要"),
            List.of("内容", "公告", "用户", "快捷入口"),
            List.of("Browse", "Search", "Open module", "Act", "Review summary"),
            List.of("标题", "分类", "更新时间", "负责人", "状态"),
            List.of("搜索", "打开入口", "收藏", "查看详情"),
            List.of("访问趋势", "内容分类"),
            List.of("搜索", "分类过滤", "快捷入口", "详情查看"),
            List.of("营销落地页", "只有介绍文案", "缺少操作入口"));

    private static final BusinessPreset GENERIC = new BusinessPreset(
            "GENERIC",
            List.of("WEB_APP"),
            List.of("总览", "核心数据", "业务列表", "详情面板"),
            List.of("业务对象", "用户", "记录"),
            List.of("Create", "Review", "Update", "Track", "Complete"),
            List.of("名称", "状态", "负责人", "更新时间"),
            List.of("新增", "编辑", "删除", "查看详情"),
            List.of("数量统计", "状态分布"),
            List.of("搜索", "过滤", "分页", "批量操作"),
            List.of("只有标题", "只有单列表", "通用空白页面"));

    private static final List<BusinessPreset> PRESETS =
            List.of(TODO, ECOMMERCE, CRM, TICKET, DASHBOARD, PORTAL, GENERIC);

    private BusinessPresetRegistry() {
    }

    public static BusinessPreset resolve(String appType, String requirement) {
        BusinessIntent intent = new RequirementIntentAnalyzer().classify(requirement);
        BusinessPreset intentPreset = switch (intent) {
            case TODO_LIST -> TODO;
            case ECOMMERCE_ADMIN, ORDER_MANAGEMENT -> ECOMMERCE;
            case CUSTOMER_CRM -> CRM;
            case TICKET_SYSTEM -> TICKET;
            case DASHBOARD -> DASHBOARD;
            default -> null;
        };
        if (intentPreset != null) {
            return intentPreset;
        }

        String normalizedAppType = appType == null ? "" : appType.trim().toUpperCase(Locale.ROOT);
        return PRESETS.stream()
                .filter(preset -> preset.appTypes().contains(normalizedAppType))
                .findFirst()
                .orElse(GENERIC);
    }

    public static List<BusinessPreset> presets() {
        return PRESETS;
    }
}
