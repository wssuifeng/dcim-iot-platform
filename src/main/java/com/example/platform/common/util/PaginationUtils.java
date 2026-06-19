package com.example.platform.common.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/*
 * 类功能:分页参数安全校验工具类
 *
 * 安全策略：
 *   - current（当前页码）下限为 1，避免 MyBatis-Plus 出现页码越界或异常
 *   - size（每页大小）限制在 1~100 之间，防止恶意大页查询拖垮数据库
 *   - 对非法参数采用“安全截断”而非抛异常，保证接口兼容性
 * */
public final class PaginationUtils {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MIN_CURRENT = 1;

    private PaginationUtils() {
    }

    /*
     * 方法功能:校验并构建安全的分页对象
     * 参数:current-当前页码, size-每页大小
     * 返回数据:Page<T>-经过安全校验的分页对象
     * */
    public static <T> Page<T> buildPage(Integer current, Integer size) {
        int[] params = validate(current, size);
        return new Page<>(params[0], params[1]);
    }

    /*
     * 方法功能:校验分页参数并返回安全的 [current, size]
     * 参数:current-当前页码, size-每页大小
     * 返回数据:int[]-索引0为安全current，索引1为安全size
     * */
    public static int[] validate(Integer current, Integer size) {
        int safeCurrent = (current == null || current < MIN_CURRENT) ? MIN_CURRENT : current;
        int safeSize;
        if (size == null || size < 1) {
            safeSize = DEFAULT_PAGE_SIZE;
        } else if (size > MAX_PAGE_SIZE) {
            safeSize = MAX_PAGE_SIZE;
        } else {
            safeSize = size;
        }
        return new int[]{safeCurrent, safeSize};
    }
}
