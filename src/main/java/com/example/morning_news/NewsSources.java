package com.example.morning_news;

import java.time.Duration;
import java.util.List;

public final class NewsSources {

    /** 並べ方 */
    public enum SortType {
        /** RSSに書かれている順番のまま */
        FEED_ORDER,
        /** ブックマーク数の多い順 */
        BOOKMARK_COUNT
    }

    /**
     * 取得元1つ分の設定。
     *
     * @param url    RSSのURL
     * @param window この期間内の記事だけに絞る。null なら絞らない
     * @param limit  表示する件数
     */
    public record FeedSource(
            String name,
            String feedName,
            String basis,
            String url,
            SortType sortType,
            Duration window,
            int limit) {
    }

    /** ジャンル1つ分の設定 */
    public record Genre(String id, String label, List<FeedSource> sources) {
    }

    private static final Duration DAY = Duration.ofHours(24);

    public static final List<Genre> GENRES = List.of(
            new Genre("domestic", "国内の時事", List.of(
                    yahoo("国内トピックス", "domestic"),
                    hatena("世の中", "social"))),
            new Genre("business", "経済・ビジネス", List.of(
                    yahoo("経済トピックス", "business"),
                    hatena("政治と経済", "economics"))),
            new Genre("it", "IT・テック", List.of(
                    yahoo("ITトピックス", "it"),
                    hatena("テクノロジー", "it"),
                    new FeedSource("Zenn", "トレンド", "24時間以内・トレンド入りの新しい順",
                            "https://zenn.dev/feed",
                            SortType.FEED_ORDER, DAY, 5),
                    new FeedSource("Qiita", "人気記事", "24時間以内・人気記事",
                            "https://qiita.com/popular-items/feed.atom",
                            SortType.FEED_ORDER, DAY, 5))));

    private static FeedSource yahoo(String feedName, String topic) {
        return new FeedSource("Yahoo!ニュース", feedName, "編集部の注目",
                "https://news.yahoo.co.jp/rss/topics/" + topic + ".xml",
                SortType.FEED_ORDER, null, 5);
    }

    private static FeedSource hatena(String feedName, String category) {
        return new FeedSource("はてなブックマーク", feedName, "24時間以内・ブックマーク数順",
                "https://b.hatena.ne.jp/hotentry/" + category + ".rss",
                SortType.BOOKMARK_COUNT, DAY, 5);
    }

    private NewsSources() {
    }
}