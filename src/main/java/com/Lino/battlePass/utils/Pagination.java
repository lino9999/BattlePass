package com.Lino.battlePass.utils;

/** Page calculations for integer-valued levels, without overflowing at the final page. */
public final class Pagination {
    private Pagination() {}

    public static int pageCount(int items, int pageSize) {
        if (pageSize < 1) throw new IllegalArgumentException("Page size must be positive");
        return (int) ((Math.max(1L, items) + pageSize - 1) / pageSize);
    }

    public static int clampPage(int page, int items, int pageSize) {
        return Math.max(1, Math.min(page, pageCount(items, pageSize)));
    }

    /** Zero means this cell has no representable positive level. */
    public static int levelAt(int page, int pageSize, int cell) {
        if (page < 1 || pageSize < 1 || cell < 0 || cell >= pageSize) return 0;
        long level = ((long) page - 1) * pageSize + cell + 1;
        return level > Integer.MAX_VALUE ? 0 : (int) level;
    }

    public static int rewardEditorPages(int maxLevel) {
        int extra = (int) Math.min(Integer.MAX_VALUE, Math.max(54L, maxLevel) + 45);
        return pageCount(extra, 45);
    }
}
