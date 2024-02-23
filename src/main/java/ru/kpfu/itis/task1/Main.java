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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

public class Main {
    private final static Pattern EXCLUDE = Pattern.compile(
            ".*(\\.(css|js|gif|jpg|png|mp3|mp4|zip|gz|svg))$");
    private static final String HOST = "https://docs.oracle.com/en/java/javase/21/docs/api/java.base/%s";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final AtomicInteger cnt = new AtomicInteger(0);
    private static final ConcurrentHashMap<Integer, String> filesMap = new ConcurrentHashMap<>();


    public static void main(String[] args) throws IOException, InterruptedException {
        File file = new File("files");
        if (file.exists()) {
            file.delete();
        }
        file.mkdir();
        String startUrl = HOST.formatted("module-summary.html");
        String result = restTemplate.getForObject(startUrl, String.class);
        if (result != null) {
            Document document = Jsoup.parse(result);
            Element body = document.body();
            Set<String> links = getLinksFromHtml(body).stream().map(HOST::formatted).collect(toSet());
            getHtmlPages(links);
            Thread.sleep(7000);
            for (File f : Objects.requireNonNull(file.listFiles())) {
                Document doc = Jsoup.parse(f);
                Element docBody = doc.body();
                Set<String> linksFromBody = getLinksFromHtml(docBody);
                String currentBaseUrl = filesMap.get(Integer.parseInt(f.getName().split("\\.")[0]));
                linksFromBody = handleRelativeLinks(currentBaseUrl, linksFromBody);
                getHtmlPages(linksFromBody);
            }
            writeIndex();
        }
    }

    private static Set<String> handleRelativeLinks(String baseUrl, Set<String> links) {
        if (baseUrl == null) return links;
        return links.stream().map(link -> {
            StringBuilder origUrl = new StringBuilder(baseUrl);
            origUrl.delete(origUrl.lastIndexOf("/"), origUrl.length());
            String regForRepeat = "/[a-zA-Z.-/]+";
            StringBuilder reg = new StringBuilder(".*(");
            boolean isRelative = false;
            while (link.startsWith("../")) {
                isRelative = true;
                reg.append(regForRepeat);
                link = link.substring(3);
            }
            if (isRelative) {
                reg.append(")");
                Pattern pattern = Pattern.compile(reg.toString());
                Matcher matcher = pattern.matcher(origUrl.toString());
                if (matcher.find()) {
                    if (matcher.group(1) != null) {
                        origUrl.delete(matcher.start(1), matcher.end(1));
                    }
                }
            }
            if (origUrl.charAt(origUrl.length() - 1) != '/') {
                origUrl.append('/');
            }
            link = origUrl.append(link).toString();
            return link;
        }).collect(toSet());
    }


    private static Set<String> getLinksFromHtml(Element body) {
        return body.getElementsByTag("a")
                .stream()
                .map(l -> l.attribute("href"))
                .filter(Objects::nonNull)
                .map(Attribute::getValue)
                .filter(l -> l.endsWith("/package-summary.html"))
                .filter(l -> !EXCLUDE.matcher(l).matches())
                .collect(toSet());
    }

    private static void getHtmlPages(Set<String> links) {
        links.parallelStream()
                .forEach(link -> {
                    int previousStep = cnt.get();
                    if (previousStep < 100) {
                        sleep(700);
                        try {
                            int currentStep = sendRequestAndWriteResponseToFile(link);
                            logStep(currentStep);
                            filesMap.put(currentStep, link);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
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

    private static int sendRequestAndWriteResponseToFile(String url) {
        String content;
        try {
            content = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            System.out.println("Error => " + e.getMessage());
            throw e;
        }

        if (content != null) {
            try {
                int i = cnt.incrementAndGet();
                Files.writeString(Path.of("files/" + i + ".html"), content, UTF_8);
                return i;
            } catch (IOException e) {
                cnt.decrementAndGet();
                throw new RuntimeException(e);
            }
        }

        throw new RuntimeException("No content");
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}