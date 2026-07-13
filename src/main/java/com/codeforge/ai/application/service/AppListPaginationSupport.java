package com.codeforge.ai.application.service;

import com.codeforge.ai.shared.request.PageRequest;
import java.util.Set;

final class AppListPaginationSupport {

    static final Set<Long> ALLOWED_PAGE_SIZES = Set.of(12L, 24L, 48L);
    static final long DEFAULT_PAGE_SIZE = 12L;

    private AppListPaginationSupport() {
    }

    static void normalize(PageRequest pageRequest) {
        if (pageRequest.getPageNo() < 1) {
            pageRequest.setPageNo(1);
        }
        if (!ALLOWED_PAGE_SIZES.contains(pageRequest.getPageSize())) {
            pageRequest.setPageSize(DEFAULT_PAGE_SIZE);
        }
    }

    static long offset(PageRequest pageRequest) {
        return (pageRequest.getPageNo() - 1) * pageRequest.getPageSize();
    }

    static long totalPages(long total, long pageSize) {
        if (total <= 0 || pageSize <= 0) {
            return 0;
        }
        return (total + pageSize - 1) / pageSize;
    }
}
