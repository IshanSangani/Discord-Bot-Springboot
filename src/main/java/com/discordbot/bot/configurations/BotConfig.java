package com.discordbot.bot.configurations;

import discord4j.core.GatewayDiscordClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BotConfig {
    @Value("${token}")
    private String token;

    @Bean
    public GatewayDiscordClient gatewayDiscordClient() {
        return discord4j.core.DiscordClientBuilder.create(token)
                .build()
                .login()
                .block();
    }

    @Bean
    public String botId(GatewayDiscordClient client) {
        return client.getSelf().block().getId().asString();
    }
}
