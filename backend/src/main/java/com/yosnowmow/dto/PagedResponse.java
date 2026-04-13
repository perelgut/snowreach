package com.yosnowmow.dto;

import java.util.List;

/**
 * Generic paginated response wrapper used by admin list endpoints.
 *
 * @param <T> the type of items in the page
 */
public class PagedResponse<T> {

    private final List<T> items;
    private final long totalCount;
    private final int page;
    private final int size;

    public PagedResponse(List<T> items, long totalCount, int page, int size) {
        this.items      = items;
        this.totalCount = totalCount;
        this.page       = page;
        this.size       = size;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public List<T> getItems()     { return items; }
    public long getTotalCount()   { return totalCount; }
    public int getPage()          { return page; }
    public int getSize()          { return size; }
}
