package com.example.platform.ingest.wecom;

import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.ResultCode;

/*
 * 类功能:企微智能机器人渠道凭证（credential_json 解析）
 *   结构：{"botId":"...","secret":"...","chatid":"..."}
 * */
public record WecomAibotCredential(String botId, String secret, String chatid) {

    /*
     * 方法功能:解析并校验凭证 JSON（botId/secret/chatid 均必填）
     * 参数:json-渠道 credential_json
     * 返回数据:凭证记录
     * */
    public static WecomAibotCredential parse(String json) {
        if (json == null || json.isBlank()) {
            throw invalid("凭证 JSON 为空");
        }
        String botId = null;
        String secret = null;
        String chatid = null;
        // 轻量解析（三字段固定结构，避免引入 Jackson 依赖到 record）
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"botId\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (m.find()) botId = m.group(1);
        m = java.util.regex.Pattern.compile("\"secret\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (m.find()) secret = m.group(1);
        m = java.util.regex.Pattern.compile("\"chatid\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (m.find()) chatid = m.group(1);

        if (botId == null || botId.isBlank()) {
            throw invalid("缺少 botId");
        }
        if (secret == null || secret.isBlank()) {
            throw invalid("缺少 secret");
        }
        if (chatid == null || chatid.isBlank()) {
            throw invalid("缺少 chatid（在群里 @机器人一句后从平台渠道页获取）");
        }
        return new WecomAibotCredential(botId, secret, chatid);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ResultCode.INVALID_PARAMETER,
                "企微智能机器人凭证无效: " + message);
    }
}
