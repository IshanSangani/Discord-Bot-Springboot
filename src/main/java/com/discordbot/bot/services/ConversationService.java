package com.discordbot.bot.services;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationService {
    private static final int MAX_TURNS = 10; // user+bot pairs
    private record Turn(String role, String content, long ts) {}
    private final Map<String, Deque<Turn>> store = new ConcurrentHashMap<>();

    private String key(String channelId, String userId){
        return channelId + ":" + userId;
    }

    public void addUserMessage(String channelId, String userId, String content){
        addTurn(channelId, userId, "user", content);
    }
    public void addBotMessage(String channelId, String userId, String content){
        addTurn(channelId, userId, "bot", content);
    }

    private void addTurn(String channelId, String userId, String role, String content){
        if (content == null || content.isBlank()) return;
        Deque<Turn> dq = store.computeIfAbsent(key(channelId,userId), k -> new ArrayDeque<>());
        synchronized (dq){
            dq.addLast(new Turn(role, trim(content, 1000), Instant.now().toEpochMilli()));
            // keep only last MAX_TURNS *2 turns (user+bot)
            while (dq.size() > MAX_TURNS * 2) dq.removeFirst();
        }
    }

    public String buildContextPrompt(String channelId, String userId, String latestUserMessage){
        Deque<Turn> dq = store.get(key(channelId,userId));
        StringBuilder sb = new StringBuilder();
        sb.append("You are Kop Ka Bot, a helpful, concise Discord bot (use light Hinglish casually, not cringe).\n");
        sb.append("Conversation so far (oldest first):\n");
        if (dq != null){
            for (Turn t : dq){
                sb.append(t.role()).append(": ").append(t.content()).append("\n");
            }
        }
        sb.append("user: ").append(latestUserMessage).append("\n");
        sb.append("Respond as bot only, do not repeat the entire context.");
        return sb.toString();
    }

    private String trim(String s, int max){
        return s.length() <= max ? s : s.substring(0,max-3) + "...";
    }
}

