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
import java.util.ArrayList;
import java.util.List;

@Service
public class GeminiService {
    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public GeminiService(@Value("${gemini.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    public Mono<String> generate(String prompt) { // alias
        return generateText(prompt);
    }

    public Mono<String> generateText(String prompt){
        if (apiKey == null || apiKey.isBlank()) {
            return Mono.just("AI not configured.");
        }
        String modifiedPrompt = "Respond to the following prompt as if you were a helpful Discord bot named Kop Ka Bot also maybe use Hinglish sometimes and dont be cringe: " + prompt;
        return sendRequest(modifiedPrompt)
                .map(this::extractTextOnly)
                .onErrorReturn("I'm having trouble connecting right now. Please try again later.");
    }

    private Mono<String> sendRequest(String prompt){
        String json = "{\n  \"contents\": [{ \n    \"parts\": [{ \"text\": " + escapeJson(prompt) + "}]\n  }]\n}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return Mono.fromCallable(() -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()))
                .map(HttpResponse::body);
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
