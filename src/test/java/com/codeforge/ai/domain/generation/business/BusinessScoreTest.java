package com.codeforge.ai.domain.generation.business;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessScoreTest {

    @Test
    void shouldPassRichEcommercePage() {
        BusinessPreset preset = BusinessPresetRegistry.resolve("ECOMMERCE", "商城后台");
        String html = """
                <aside>商品管理 订单管理 库存管理 分类管理 用户管理</aside>
                <section>销售额 订单趋势 库存预警 图表</section>
                <input placeholder="搜索商品"><select><option>分类筛选</option></select>
                <button>新增商品</button><button>编辑商品</button><button>删除</button><button>批量操作</button>
                <table><tr><td>商品名称 价格 库存 订单状态 购买用户</td></tr></table>
                """;

        BusinessScore score = BusinessScoreEvaluator.evaluate(html, preset);

        assertThat(score.score()).isGreaterThanOrEqualTo(BusinessScoreEvaluator.PASSING_SCORE);
        assertThat(score.moduleCount()).isGreaterThanOrEqualTo(4);
        assertThat(score.entityCount()).isGreaterThanOrEqualTo(4);
        assertThat(score.hasSearch()).isTrue();
        assertThat(score.hasFilter()).isTrue();
    }

    @Test
    void shouldRejectTitleOnlyTodoPage() {
        BusinessPreset preset = BusinessPresetRegistry.resolve("WEB_APP", "待办清单");
        String html = "<h1>今日任务</h1><button>新增</button><button>删除</button><button>完成</button>";

        BusinessScore score = BusinessScoreEvaluator.evaluate(html, preset);

        assertThat(score.score()).isLessThan(BusinessScoreEvaluator.PASSING_SCORE);
        assertThat(score.moduleCount()).isLessThan(3);
    }
}
