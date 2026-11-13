package com.example.morning_news;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    /** ニュースを返す（今日すでに取得済みなら保持している結果） */
    @GetMapping("/api/news")
    public NewsService.NewsResponse news() {
        return newsService.get(false);
    }

    /** 更新ボタン用：必ず取り直して返す */
    @PostMapping("/api/news/refresh")
    public NewsService.NewsResponse refresh() {
        return newsService.get(true);
    }
}