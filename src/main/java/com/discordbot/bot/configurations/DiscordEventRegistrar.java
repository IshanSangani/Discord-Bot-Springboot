package com.discordbot.bot.configurations;

import com.discordbot.bot.listeners.EventListeners;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.Event;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiscordEventRegistrar {
    private final GatewayDiscordClient client;
    private final List<EventListeners<? extends Event>> listeners;

    public DiscordEventRegistrar(GatewayDiscordClient client, List<EventListeners<? extends Event>> listeners) {
        this.client = client;
        this.listeners = listeners;
    }

    @PostConstruct
    public void register() {
        for (EventListeners<? extends Event> listener : listeners) {
            registerListener(listener);
        }
    }

    private <T extends Event> void registerListener(EventListeners<T> listener) {
        client.on(listener.getEventType())
                .flatMap(listener::execute)
                .onErrorResume(listener::handleError)
                .subscribe();
    }
}

