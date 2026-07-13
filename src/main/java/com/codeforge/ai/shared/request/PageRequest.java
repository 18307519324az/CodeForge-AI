package com.codeforge.ai.shared.request;

import lombok.Data;

@Data
public class PageRequest {

    private long pageNo = 1;

    private long pageSize = 10;

    private String keyword;

    private String sortField;

    private String sortOrder;
}
