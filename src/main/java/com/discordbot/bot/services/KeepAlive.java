package com.discordbot.bot.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class KeepAlive {
    @Scheduled(fixedRate = 1*1000*60) // every 1 minute
    public void keepAlive() {
        System.out.println("Bot is alive");
    }
}
