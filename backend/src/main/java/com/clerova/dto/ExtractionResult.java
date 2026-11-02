package com.clerova.dto;

import com.clerova.domain.Category;
import com.clerova.domain.Priority;

public class ExtractionResult {

    public Category category;
    public Priority priority;
    public String summary;
    public String address;
    /**
     * An automatic notice that needs nothing from the company (a receipt,
     * shipping update, statement). It's set aside instead of becoming a request.
     */
    public boolean automaticNotice;
}
