package com.moke.aidemo;

import org.json.JSONObject;

/**
 * 从模型回复中解析本地可执行指令。
 */
public final class AgentActionParser {

    private static final String TAG_START = "<agent_action>";
    private static final String TAG_END = "</agent_action>";

    private AgentActionParser() {
    }

    public static final class ParseResult {
        public final String displayText;
        public final JSONObject action;

        ParseResult(String displayText, JSONObject action) {
            this.displayText = displayText;
            this.action = action;
        }
    }

    public static ParseResult parse(String reply) {
        if (reply == null) {
            return new ParseResult("", null);
        }
        int start = reply.indexOf(TAG_START);
        int end = reply.indexOf(TAG_END);
        if (start < 0 || end <= start) {
            return new ParseResult(reply.trim(), null);
        }
        String jsonPart = reply.substring(start + TAG_START.length(), end).trim();
        String visible = (reply.substring(0, start) + reply.substring(end + TAG_END.length())).trim();
        try {
            JSONObject action = new JSONObject(jsonPart);
            return new ParseResult(visible, action);
        } catch (Exception e) {
            return new ParseResult(reply.trim(), null);
        }
    }
}
