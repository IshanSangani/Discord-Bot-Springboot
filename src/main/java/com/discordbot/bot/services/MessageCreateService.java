package com.discordbot.bot.services;

import com.discordbot.bot.listeners.EventListeners;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.*;

@Service
public class MessageCreateService implements EventListeners<MessageCreateEvent> {
    private final String ownerDisplayName;
    private final GeminiService geminiService;
    private final ModerationService moderationService;
    private final ConversationService conversationService;

    public MessageCreateService(@Value("${owner.displayName:Ishan}") String ownerDisplayName,
                                GeminiService geminiService,
                                ModerationService moderationService,
                                ConversationService conversationService) {
        this.ownerDisplayName = ownerDisplayName;
        this.geminiService = geminiService;
        this.moderationService = moderationService;
        this.conversationService = conversationService;
    }

    @Override
    public Class<MessageCreateEvent> getEventType() { return MessageCreateEvent.class; }

    @Override
    public Mono<Void> execute(MessageCreateEvent event) {
        if (event.getMessage().getAuthor().map(author -> author.isBot()).orElse(true)) return Mono.empty();
        String content = event.getMessage().getContent();
        Member member = event.getMember().orElse(null);
        String displayName = member != null ? member.getDisplayName() : event.getMessage().getAuthor().map(u -> u.getUsername()).orElse("User");
        String lower = content.toLowerCase(Locale.ROOT);
        Snowflake selfId = event.getClient().getSelfId();

        return moderationService.isAbusive(content)
                .flatMap(abusive -> {
                    if (abusive && !ownerDisplayName.equalsIgnoreCase(displayName)) {
                        return event.getMessage().getChannel()
                                .flatMap(ch -> ch.createMessage(displayName + ",\nYour message was flagged by moderation. Please avoid abusive or hateful language."))
                                .then(event.getMessage().delete())
                                .then();
                    }
                    if (lower.startsWith("hi kop ka bot") && ownerDisplayName.equalsIgnoreCase(displayName)) {
                        return reply(event, "Hello KING Kop");
                    }
                    if (lower.startsWith("hi kop ka bot") || lower.startsWith("hello kop ka bot")) {
                        return reply(event, "Hello, " + displayName + " 👋");
                    }
                    String mentionSimple = "<@" + selfId.asString() + ">";
                    String mentionNick = "<@!" + selfId.asString() + ">";
                    boolean isMention = content.contains(mentionSimple) || content.contains(mentionNick);

                    Message msg = event.getMessage();
                    boolean isReplyToBot = msg.getReferencedMessage()
                            .map(ref -> ref.getAuthor().map(a -> a.getId().equals(selfId)).orElse(false))
                            .orElse(false);

                    if (isMention || isReplyToBot) {
                        String cleaned = content.replace(mentionSimple, "").replace(mentionNick, "").trim();
                        if (cleaned.isBlank()) cleaned = content.trim();
                        String channelId = msg.getChannelId().asString();
                        String userId = msg.getAuthor().map(a -> a.getId().asString()).orElse("user");
                        conversationService.addUserMessage(channelId, userId, cleaned);
                        String contextPrompt = conversationService.buildContextPrompt(channelId, userId, cleaned);
                        return msg.getChannel()
                                .flatMap(MessageChannel::type)
                                .then(geminiService.generateText(contextPrompt))
                                .onErrorResume(err -> Mono.just("Sorry, I couldn't process your request at the moment."))
                                .flatMap(resp -> msg.getChannel().flatMap(ch -> ch.createMessage(resp)
                                        .doOnSuccess(sent -> conversationService.addBotMessage(channelId, userId, resp))))
                                .then();
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> reply(MessageCreateEvent e, String content) {
        return e.getMessage().getChannel().flatMapMany(ch -> {
            List<String> parts = splitContent(content, 1900);
            return Flux.fromIterable(parts).concatMap(ch::createMessage);
        }).then();
    }

    private List<String> splitContent(String content, int max) {
        if (content.length() <= max) return List.of(content);
        List<String> parts = new ArrayList<>();
        int idx = 0;
        while (idx < content.length()) {
            int end = Math.min(idx + max, content.length());
            if (end < content.length()) {
                int newline = content.lastIndexOf('\n', end);
                int space = content.lastIndexOf(' ', end);
                int breakPos = Math.max(newline, space);
                if (breakPos > idx + max / 2) {
                    end = breakPos + 1;
                }
            }
            parts.add(content.substring(idx, end).trim());
            idx = end;
        }
        return parts;
    }
}
