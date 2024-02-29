package ru.kpfu.itis.task2;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.*;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyMap;


public class Solution {
    private static final CountDownLatch latch = new CountDownLatch(100);
    private static final Pattern EXCLUDE = Pattern.compile("-?[—/'@ “”0-9\\p{Punct}©]");

    public static void main(String[] args) throws InterruptedException {

        Map<String, Set<String>> lemmaMap = stream(Objects.requireNonNull(new File("files").listFiles()))
                .parallel()
                .filter(file -> !file.getName().contains("index"))
                .filter(file -> isRegularFile(file.toPath()))
                .map(file -> {
                    try {
                        Document html = Jsoup.parse(file);
                        Element body = html.body();
                        String text = body.text();

                        Annotation document = getAnnotation(text);
                        // Создаем карту для группировки токенов по леммам
                        Map<String, Set<String>> buffer = new HashMap<>();
                        // Обрабатываем каждое предложение в тексте
                        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
                            // Обрабатываем каждый токен в предложении
                            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                                String word = token.originalText();
                                if (EXCLUDE.matcher(word).find() || isSingleChar(word)) {
                                    continue; // skip trash words
                                }
                                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                                // Добавляем токен в группу леммы
                                if (buffer.containsKey(lemma)) {
                                    buffer.get(lemma).add(word);
                                } else {
                                    Set<String> wordList = new HashSet<>();
                                    wordList.add(word);
                                    buffer.put(lemma, wordList);
                                }
                            }
                        }
                        return buffer;
                    } catch (IOException e) {
                        throw new RuntimeException("Failed Read file: " + file.getName(), e);
                    } finally {
                        latch.countDown();
                    }
                }).reduce(((map1, map2) -> {
                    map1.putAll(map2);
                    return map1;
                })).orElse(emptyMap());

        latch.await(); // wait while all files was processed
        writeOutputToFiles(lemmaMap);
    }

    private static boolean isSingleChar(String word) {
        return word.trim().length() == 1 && Character.isLetter(word.charAt(0));
    }

    private static void writeOutputToFiles(Map<String, Set<String>> lemmaMap) {
        File lemmas = new File("files/output/task2/", "lemma.txt");
        File tokens = new File("files/output/task2/", "tokens.txt");

        try {
            deleteIfExists(lemmas.toPath());
            deleteIfExists(tokens.toPath());
            createFile(lemmas.toPath());
            createFile(tokens.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Set<String> uniqTokens = lemmaMap.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
        StringBuilder tokensAsString = new StringBuilder();
        StringBuilder lemmasAsString = new StringBuilder();
        uniqTokens.forEach(t -> tokensAsString.append(t).append("\n"));
        lemmaMap.forEach((k, v) -> lemmasAsString.append(k).append(" ").append(String.join(" ", v)).append("\n"));

        try {
            writeString(tokens.toPath(), tokensAsString, UTF_8);
            writeString(lemmas.toPath(), lemmasAsString, UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Cannot write output to file", e);
        }
    }


    private static Annotation getAnnotation(String text) {
        String normalized = text.toLowerCase()
                .replace("of", "")
                .replace("at", "")
                .replace("the", "")
                .replace("over", "")
                .replace(".", "");

        // Создаем объект аннотатора StanfordCoreNLP с нужными нам аннотаторами
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // Создаем объект аннотации для обработки текста
        Annotation document = new Annotation(normalized);

        // Запускаем аннотатор для обработки текста
        pipeline.annotate(document);
        return document;
    }
}
