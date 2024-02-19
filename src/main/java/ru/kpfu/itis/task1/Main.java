package ru.kpfu.itis.task1;

//import org.springframework.web.client.RestTemplate;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        String startUrl = HOST.formatted("ru/feed/");
        String result = restTemplate.getForObject(startUrl, String.class);

        if (result != null) {
            Document document = Jsoup.parse(result);
            Element body = document.body();

            Set<String> links = body.getElementsByTag("a")
                    .stream()
                    .map(l -> l.attribute("href"))
                    .filter(Objects::nonNull)
                    .map(Attribute::getValue)
                    .filter(l -> l.startsWith("/ru"))
                    .filter(l -> !EXCLUDE.matcher(l).matches())
                    .collect(toSet());

            links.parallelStream()
                    .forEach(link -> {
                        sleep(700);
                        int i = cnt.incrementAndGet();
                        if (i <= 100) {
                            System.out.println(Thread.currentThread().getName() + " => " + i);
                            sendRequestAndWriteResponseToFile(link, i);
                            filesMap.put(i, link);
                        }
                    });

            try (BufferedWriter writer = new BufferedWriter(new FileWriter("files/index.txt"))) {
                filesMap.forEach((k, v) -> write(k, v, writer));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


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