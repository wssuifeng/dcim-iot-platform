package com.example.platform.common.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Excel 导出工具类，统一设置响应头与文件名
 */
public final class ExcelExportUtil {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ExcelExportUtil() {
    }

    /**
     * 设置 Excel 下载响应头
     *
     * @param response HttpServletResponse
     * @param fileName 不含后缀的文件名
     */
    public static void setExcelResponse(HttpServletResponse response, String fileName) {
        String encodedFileName = URLEncoder.encode(fileName + ".xlsx", StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                        .filename(encodedFileName)
                        .build()
                        .toString());
    }

    /**
     * 生成带时间戳的导出文件名
     *
     * @param prefix 文件前缀
     * @return 文件名（不含后缀）
     */
    public static String buildFileName(String prefix) {
        return prefix + "_" + LocalDateTime.now().format(DATE_FORMAT);
    }
}
