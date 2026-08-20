package com.skala.ch07;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class Lab2IngestService {

    private final VectorStore vectorStore;
    private final int chunkSize;
    private final int minChunkSizeChars;
    private final double chunkOverlapRatio;

    public Lab2IngestService(VectorStore vectorStore,
            @Value("${lab2.chunk-size}") int chunkSize,
            @Value("${lab2.min-chunk-size-chars}") int minChunkSizeChars,
            @Value("${lab2.chunk-overlap-ratio:0.0}") double chunkOverlapRatio) {
        this.vectorStore = vectorStore;
        this.chunkSize = chunkSize;
        this.minChunkSizeChars = minChunkSizeChars;
        this.chunkOverlapRatio = chunkOverlapRatio;
    }

    public record IngestResult(String source, int chunks) {}

    public IngestResult ingest(Resource doc, String source, String version) {
        var reader = new TextReader(doc);
        reader.getCustomMetadata().put("source", source);
        reader.getCustomMetadata().put("version", version);

        var splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(minChunkSizeChars)
                .build();
        List<Document> raw = reader.get();
        List<Document> tagged = raw.stream()
            .map(d -> new Document(d.getText(), Map.of("source", source, "version", version)))
            .toList();
        List<Document> chunks = applyOverlap(splitter.apply(tagged), chunkOverlapRatio);

        vectorStore.delete(new FilterExpressionBuilder()
                .eq("source", source).build());
        vectorStore.add(chunks);
        return new IngestResult(source, chunks.size());
    }

    /**
     * TokenTextSplitter는 겹침(overlap)을 지원하지 않는다. 그래서 분할 후
     * 각 청크 앞에 바로 이전 청크의 끝부분 일부를 덧붙이는 방식으로 흉내 낸다.
     * 경계에서 잘린 문장을 다음 청크가 다시 품을 수 있게 하는 것이 목적이다.
     */
    private List<Document> applyOverlap(List<Document> chunks, double ratio) {
        if (ratio <= 0.0) {
            return chunks;
        }
        List<Document> result = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Document current = chunks.get(i);
            String text = current.getText();
            if (i > 0) {
                String prevText = chunks.get(i - 1).getText();
                int overlapLen = (int) (prevText.length() * ratio);
                String prefix = prevText.substring(Math.max(0, prevText.length() - overlapLen));
                text = prefix + text;
            }
            result.add(new Document(text, current.getMetadata()));
        }
        return result;
    }
}
