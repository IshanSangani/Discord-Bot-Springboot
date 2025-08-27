package com.discordbot.bot.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple AI-based moderation using GeminiService (text model) instead of hardcoded slur list.
 * It asks the model to return strict JSON so we can parse a toxicity flag.
 * NOTE: Relies on the generative model; add rate limiting / batching for production.
 */
@Service
public class ModerationService {
    private final GeminiService geminiService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    record CacheEntry(boolean abusive, long ts) {}

    public ModerationService(GeminiService geminiService) { this.geminiService = geminiService; }

    public Mono<Boolean> isAbusive(String content) {
        if (content == null || content.isBlank()) return Mono.just(false);
        CacheEntry ce = cache.get(content);
        long now = Instant.now().toEpochMilli();
        if (ce != null && (now - ce.ts) < CACHE_TTL_MS) {
            return Mono.just(ce.abusive);
        }
        String instruction = "You are a strict moderation classifier. Return ONLY compact JSON. " +
                "Classify the following Discord message for abusive / harassing / hateful or explicit sexual content. " +
                "If abusive OR hateful OR severely profane return {\"abusive\":true,\"reason\":\"short reason\"} else {\"abusive\":false}. " +
                "Message: >>>" + content + "<<<";
        return geminiService.generateText(instruction)
                .map(this::parseAbusiveFlag)
                .onErrorReturn(false)
                .doOnNext(flag -> cache.put(content, new CacheEntry(flag, Instant.now().toEpochMilli())));
    }

    private boolean parseAbusiveFlag(String modelResp) {
        try {
            // Attempt to locate JSON object in response
            int start = modelResp.indexOf('{');
            int end = modelResp.lastIndexOf('}');
            if (start >= 0 && end > start) {
                String json = modelResp.substring(start, end + 1);
                JsonNode node = mapper.readTree(json);
                if (node.has("abusive")) {
                    return node.get("abusive").asBoolean(false);
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}

