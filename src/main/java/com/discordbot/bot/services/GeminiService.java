package com.discordbot.bot.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeminiService {
    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    // Simple cache: prompt -> response
    private final Map<String, String> responseCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 3600000; // 1 hour cache

    public GeminiService(@Value("${gemini.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public Mono<String> generate(String prompt) { // alias
        return generateText(prompt);
    }

    public Mono<String> generateText(String prompt){
        System.out.println("🔍 generateText called with prompt: " + prompt);
        System.out.println("📝 API Key present: " + (apiKey != null && !apiKey.isBlank()));
        
        // Check cache first
        String cacheKey = prompt.hashCode() + "";
        if (responseCache.containsKey(cacheKey)) {
            Long timestamp = cacheTimestamps.get(cacheKey);
            if (timestamp != null && (System.currentTimeMillis() - timestamp) < CACHE_DURATION_MS) {
                System.out.println("💾 Cache HIT: Returning cached response");
                return Mono.just(responseCache.get(cacheKey));
            }
        }
        
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("❌ API key is missing or blank!");
            return Mono.just("AI not configured.");
        }
        String modifiedPrompt = "Respond to the following prompt as if you were a helpful Discord bot named Kop Ka Bot also maybe use Hinglish sometimes and dont be cringe: " + prompt;
        return sendRequest(modifiedPrompt)
                .doOnNext(response -> System.out.println("✅ Raw API Response received"))
                .map(this::extractTextOnly)
                .doOnNext(extracted -> {
                    System.out.println("🎯 Extracted text: " + extracted);
                    // Cache the response
                    responseCache.put(cacheKey, extracted);
                    cacheTimestamps.put(cacheKey, System.currentTimeMillis());
                    System.out.println("💾 Response cached");
                })
                .onErrorResume(err -> {
                    System.out.println("❌ Error in generateText: " + err.getMessage());
                    if (err.getMessage().contains("429") || err.getMessage().contains("quota")) {
                        return Mono.just("I'm a bit busy right now (API limit reached). Try again in a few seconds! 🚀");
                    }
                    return Mono.just("I'm having trouble connecting right now. Please try again later.");
                });
    }

    private Mono<String> sendRequest(String prompt){
        String json = "{\n  \"contents\": [{ \n    \"parts\": [{ \"text\": " + escapeJson(prompt) + "}]\n  }]\n}";
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
        
        System.out.println("🌐 Calling Gemini API: " + url.substring(0, url.indexOf("?key=")) + "?key=***");
        System.out.println("📨 Request JSON: " + json);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return Mono.fromCallable(() -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()))
                .doOnNext(response -> {
                    System.out.println("🔔 HTTP Status: " + response.statusCode());
                    System.out.println("📄 Response Body: " + response.body());
                })
                .flatMap(response -> {
                    if (response.statusCode() == 429) {
                        System.out.println("⚠️ QUOTA EXCEEDED: Free tier API limit reached. Upgrade to paid plan or wait for quota reset.");
                        return Mono.error(new RuntimeException("API quota exceeded (429). Free tier limit reached."));
                    }
                    if (response.statusCode() >= 400) {
                        System.out.println("❌ API Error " + response.statusCode() + ": " + response.body());
                        return Mono.error(new RuntimeException("API error: " + response.statusCode()));
                    }
                    return Mono.just(response.body());
                })
                .doOnError(err -> System.out.println("❌ Error: " + err.getMessage()));
    }

    private String escapeJson(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private String extractTextOnly(String body){
        try {
            JsonNode root = mapper.readTree(body);
            List<String> texts = new ArrayList<>();
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray()) {
                for (JsonNode cand : candidates) {
                    JsonNode parts = cand.path("content").path("parts");
                    if (parts.isArray()) {
                        for (JsonNode part : parts) {
                            if (part.has("text")) {
                                texts.add(part.get("text").asText());
                            }
                        }
                    }
                }
            }
            if (!texts.isEmpty()) return String.join("\n", texts);
        } catch (Exception ignored) {}
        return "No response.";
    }
}
