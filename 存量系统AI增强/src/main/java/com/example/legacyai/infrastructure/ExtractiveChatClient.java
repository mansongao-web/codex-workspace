package com.example.legacyai.infrastructure;

import com.example.legacyai.domain.Citation;
import com.example.legacyai.ports.ChatClient;

import java.util.List;

public final class ExtractiveChatClient implements ChatClient {
    @Override
    public String answer(String question, List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "未在知识库中检索到足够相关的资料。建议补充制度文档、FAQ 或产品资料后再查询。";
        }

        StringBuilder answer = new StringBuilder();
        answer.append("根据知识库检索结果，可以参考以下内容：");
        int limit = Math.min(3, citations.size());
        for (int i = 0; i < limit; i++) {
            Citation citation = citations.get(i);
            answer.append("\n\n")
                    .append(i + 1)
                    .append(". ")
                    .append(citation.snippet())
                    .append(" [")
                    .append(citation.index())
                    .append("]");
        }
        answer.append("\n\n以上为抽取式回答；接入大模型后可生成更自然的总结，但仍会保留引用来源。");
        return answer.toString();
    }

    @Override
    public boolean generatedByModel() {
        return false;
    }
}
