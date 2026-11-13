package com.example.morning_news;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    /** 画面に返す全体の結果。fetchedAt は各サイトから取得した時刻 */
    public record NewsResponse(Instant fetchedAt, List<GenreResult> genres) {
    }

    /** ジャンル1つ分の結果 */
    public record GenreResult(String id, String label, List<SourceResult> sources) {
    }

    /**
     * 取得元1つ分の結果。
     * 失敗したときは items が空で error にメッセージ。
     * 期間の絞り込みで件数が少ないときは note に注意書き。
     */
    public record SourceResult(
            String name,
            String feedName,
            String basis,
            List<NewsItem> items,
            String note,
            String error) {
    }

    private final RssFetcher rssFetcher;

    /** 前回取得した全ジャンルの結果。まだ一度も取得していなければ null */
    private NewsResponse cache;

    public NewsService(RssFetcher rssFetcher) {
        this.rssFetcher = rssFetcher;
    }

    /**
     * 全ジャンルのニュースを返す。
     * 今日（日本時間）すでに取得済みならその結果を返し、
     * まだ取得していない・前日以前の結果・強制更新のときは取り直す。
     */
    public synchronized NewsResponse get(boolean forceRefresh) {
        Instant now = Instant.now();

        if (forceRefresh || !isFetchedToday(now)) {
            log.info("ニュースを取得します（強制更新: {}）", forceRefresh);
            cache = new NewsResponse(now, loadAll(now));
        }
        return cache;
    }

    /** 保持している結果が、日本時間で今日取得したものかどうか */
    private boolean isFetchedToday(Instant now) {
        if (cache == null) {
            return false;
        }
        LocalDate fetchedDate = LocalDate.ofInstant(cache.fetchedAt(), JST);
        LocalDate today = LocalDate.ofInstant(now, JST);
        return fetchedDate.equals(today);
    }

    private List<GenreResult> loadAll(Instant now) {
        List<GenreResult> genres = new ArrayList<>();
        for (NewsSources.Genre genre : NewsSources.GENRES) {
            List<SourceResult> sources = new ArrayList<>();
            for (NewsSources.FeedSource source : genre.sources()) {
                sources.add(load(source, now));
            }
            genres.add(new GenreResult(genre.id(), genre.label(), sources));
        }
        return genres;
    }

    private SourceResult load(NewsSources.FeedSource source, Instant now) {
        try {
            Stream<NewsItem> stream = rssFetcher.fetch(source.url()).stream();

            // 期間の絞り込み（window が null なら絞らない）
            if (source.window() != null) {
                Instant since = now.minus(source.window());
                stream = stream.filter(item ->
                        item.publishedAt() != null && item.publishedAt().isAfter(since));
            }

            // 並べ替え
            stream = switch (source.sortType()) {
                case BOOKMARK_COUNT -> stream.sorted(Comparator
                        .comparing((NewsItem item) ->
                                item.bookmarkCount() == null ? 0 : item.bookmarkCount())
                        .reversed());
                case FEED_ORDER -> stream;
            };

            List<NewsItem> top = stream.limit(source.limit()).toList();
            String note = noteFor(source, top.size());
            return new SourceResult(source.name(), source.feedName(), source.basis(),
                    top, note, null);

        } catch (Exception e) {
            log.warn("取得に失敗しました: {}", source.url(), e);
            return new SourceResult(source.name(), source.feedName(), source.basis(),
                    List.of(), null, "取得に失敗しました");
        }
    }

    /** 期間の絞り込みで件数が少なくなったときの注意書き。問題なければ null */
    private String noteFor(NewsSources.FeedSource source, int count) {
        if (source.window() == null || count >= source.limit()) {
            return null;
        }
        long hours = source.window().toHours();
        if (count == 0) {
            return "直近" + hours + "時間の記事はありませんでした";
        }
        return "直近" + hours + "時間の記事が少ないため、" + count + "件のみ表示しています";
    }
}