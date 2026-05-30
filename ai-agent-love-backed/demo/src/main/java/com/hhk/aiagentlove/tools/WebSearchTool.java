package com.hhk.aiagentlove.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(
            @ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            JSONObject jsonObject = JSONUtil.parseObj(response);
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (organicResults == null || organicResults.isEmpty()) {
                return "未找到与「" + query + "」相关的搜索结果。";
            }
            int limit = Math.min(5, organicResults.size());
            List<Object> topResults = organicResults.subList(0, limit);
            return IntStream.range(0, topResults.size())
                    .mapToObj(i -> formatResult(i + 1, (JSONObject) topResults.get(i)))
                    .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            return "搜索失败：" + e.getMessage();
        }
    }

    private String formatResult(int index, JSONObject item) {
        String title = item.getStr("title", "无标题");
        String snippet = item.getStr("snippet", "");
        String link = item.getStr("link", "");
        StringBuilder sb = new StringBuilder();
        sb.append(index).append(". ").append(title);
        if (!snippet.isBlank()) {
            sb.append("\n   摘要：").append(snippet);
        }
        if (!link.isBlank()) {
            sb.append("\n   链接：").append(link);
        }
        return sb.toString();
    }
}
