package ru.kpfu.itis.task1;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

public class Main {
    private final static Pattern EXCLUDE = Pattern.compile(
            ".*(\\.(css|js|gif|jpg|png|mp3|mp4|zip|gz|svg))$");
    private static final String HOST = "https://habr.com/%s";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final AtomicInteger cnt = new AtomicInteger(0);
    private static final ConcurrentHashMap<Integer, String> filesMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        File file = new File("files");
        if (file.exists()) {
            file.delete();
        }
        file.mkdir();
        String startUrl = HOST.formatted("ru/feed/");
        String result = restTemplate.getForObject(startUrl, String.class);
        if (result != null) {
            Document document = Jsoup.parse(result);
            Element body = document.body();
            Set<String> links = getLinksFromHtml(body);
            getHtmlPages(links);
            writeIndex();
        }
    }

    private static Set<String> getLinksFromHtml(Element body) {
        return body.getElementsByTag("a")
                .stream()
                .map(l -> l.attribute("href"))
                .filter(Objects::nonNull)
                .map(Attribute::getValue)
                .filter(l -> l.startsWith("/ru"))
                .filter(l -> !EXCLUDE.matcher(l).matches())
                .collect(toSet());
    }

    private static void getHtmlPages(Set<String> links) {
        links.parallelStream()
                .forEach(link -> {
                    int i = cnt.incrementAndGet();
                    if (i <= 100) {
                        sleep(700);
                        logStep(i);
                        sendRequestAndWriteResponseToFile(link, i);
                        filesMap.put(i, link);
                    }
                });
    }

    private static void writeIndex() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("files/index.txt"))) {
            filesMap.entrySet()
                    .stream()
                    .sorted(Comparator.comparingInt(Map.Entry::getKey))
                    .forEach((e) -> write(e.getKey(), e.getValue(), writer));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void logStep(int i) {
        System.out.println(Thread.currentThread().getName() + " => " + i);
    }

    private static void write(Integer k, String v, BufferedWriter writer) {
        try {
            writer.write(k + " => " + v + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendRequestAndWriteResponseToFile(String url, int fileName) {
        String content = restTemplate.getForObject(HOST.formatted(url), String.class);
        if (content != null) {
            try {
                Files.writeString(Path.of("files/" + fileName + ".html"), content, UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}