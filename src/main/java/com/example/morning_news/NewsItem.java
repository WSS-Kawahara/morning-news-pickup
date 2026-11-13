package com.example.morning_news;

import java.time.Instant;

public record NewsItem(
        String title,
        String link,
        Instant publishedAt,
        String siteName,
        Integer bookmarkCount) {
}