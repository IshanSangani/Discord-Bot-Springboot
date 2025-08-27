package com.discordbot.bot.listeners;


import discord4j.core.object.entity.User;
import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;

import java.util.List;

public abstract class MessageListener {
    private String author = "UNKNOWN";
    private final String botId;

    public MessageListener(String botId) {
        this.botId = botId;
    }

    public Mono<Void> processMessage(final Message eventMessage){
        return Mono.just(eventMessage)
                .filter(message->{
                    final Boolean isNotBot = message.getAuthor()
                            .map(user -> !user.isBot())
                            .orElse(false);
                    if(isNotBot){
                        message.getAuthor().ifPresent(user->author = user.getUsername());
                    }
                    // Only respond if the bot is mentioned
                    List<User> mentionedUsers = message.getUserMentions();
                    boolean isBotMentioned = mentionedUsers.stream().anyMatch(user -> user.getId().asString().equals(botId));
                    return isNotBot && isBotMentioned;
                })
                .flatMap(Message::getChannel)
                .flatMap(channel -> channel.createMessage(String.format("Hello, %s!", author)))
                .then();
    }
}
