package com.discordbot.bot.services;

import com.discordbot.bot.listeners.EventListeners;
import com.discordbot.bot.listeners.MessageListener;
import discord4j.core.event.domain.message.MessageUpdateEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class MessageUpdateService extends MessageListener implements EventListeners<MessageUpdateEvent>{
    @Autowired
    public MessageUpdateService(@Qualifier("botId") String botId) {
        super(botId);
    }

    @Override
    public Class<MessageUpdateEvent> getEventType() {
        return MessageUpdateEvent.class;
    }
    @Override
    public Mono<Void> execute(MessageUpdateEvent event) {
        return Mono.just(event)
                .filter(MessageUpdateEvent::isContentChanged)
                .flatMap(MessageUpdateEvent::getMessage)
                .flatMap(super::processMessage);
    }
}
