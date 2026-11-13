package com.example.morning_news;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
public class RssFetcher {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public List<NewsItem> fetch(String feedUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feedUrl))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "morning-news-pickup/0.1 (personal use)")
                .GET()
                .build();

        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IllegalStateException("RSSの取得に失敗しました: " + response.statusCode());
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        try (InputStream body = response.body()) {
            Document document = builder.parse(body);

            // RSS（Yahoo!・はてな・Zenn）は <item>、Atom（Qiita）は <entry>
            NodeList entries = document.getElementsByTagName("item");
            if (entries.getLength() == 0) {
                entries = document.getElementsByTagName("entry");
            }

            List<NewsItem> result = new ArrayList<>();
            for (int i = 0; i < entries.getLength(); i++) {
                Element entry = (Element) entries.item(i);
                String link = cleanLink(linkOf(entry));

                result.add(new NewsItem(
                        textOf(entry, "title"),
                        link,
                        publishedAtOf(entry),
                        siteNameOf(link),
                        bookmarkCountOf(entry)));
            }
            return result;
        }
    }

    /** 指定したタグの最初の1つの中身を返す。なければ null */
    private String textOf(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent().trim();
        return text.isEmpty() ? null : text;
    }

    /** リンクを取り出す。Atom は href 属性、RSS はタグの中身 */
    private String linkOf(Element entry) {
        NodeList links = entry.getElementsByTagName("link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String href = link.getAttribute("href");
            String rel = link.getAttribute("rel");
            if (!href.isEmpty() && (rel.isEmpty() || rel.equals("alternate"))) {
                return href;
            }
            String text = link.getTextContent().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    /** アクセス解析用の ?utm_... を取り除く */
    private String cleanLink(String link) {
        if (link == null) {
            return null;
        }
        int index = link.indexOf("?utm_");
        return index >= 0 ? link.substring(0, index) : link;
    }

    /** 日時を取り出す。サイトによってタグ名が違うので順番に探す */
    private Instant publishedAtOf(Element entry) {
        String[] tagNames = {"pubDate", "dc:date", "published", "updated"};
        for (String tagName : tagNames) {
            String text = textOf(entry, tagName);
            if (text != null) {
                return parseDate(text);
            }
        }
        return null;
    }

    /** 日時の書き方の違いに対応して Instant に変換する */
    private Instant parseDate(String text) {
        // 例: 2026-11-05T23:00:29Z / 2026-11-04T20:38:50+09:00
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
            // 次の形式を試す
        }
        // 例: Fri, 06 Nov 2026 05:04:08 GMT
        try {
            return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** リンクのURLからドメイン部分をサイト名として取り出す */
    private String siteNameOf(String link) {
        if (link == null) {
            return null;
        }
        try {
            String host = URI.create(link).getHost();
            if (host == null) {
                return null;
            }
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** はてなブックマークのブックマーク数。ない場合は null */
    private Integer bookmarkCountOf(Element entry) {
        String text = textOf(entry, "hatena:bookmarkcount");
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
