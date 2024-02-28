package ru.kpfu.itis.task2;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

import java.util.*;


public class Solution {
    public static void main(String[] args) {
        Annotation document = getAnnotation();

        // Создаем карту для группировки токенов по леммам
        Map<String, List<String>> lemmaMap = new HashMap<>();

        // Обрабатываем каждое предложение в тексте
        for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
            // Обрабатываем каждый токен в предложении
            for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                String word = token.originalText();
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);

                // Добавляем токен в группу леммы
                if (lemmaMap.containsKey(lemma)) {
                    lemmaMap.get(lemma).add(word);
                } else {
                    List<String> wordList = new ArrayList<>();
                    wordList.add(word);
                    lemmaMap.put(lemma, wordList);
                }
            }
        }

        // Выводим результат группировки токенов по леммам
        for (Map.Entry<String, List<String>> entry : lemmaMap.entrySet()) {
            System.out.println("Лемма: " + entry.getKey());
            System.out.println("Токены: " + entry.getValue());
            System.out.println();
        }
    }

    private static Annotation getAnnotation() {
        String text = "The quick brown fox jumps over the lazy dog. The dogs barks loudly at the sight of the fox.".toLowerCase()
                .replace("of","")
                .replace("at", "")
                .replace("the", "")
                .replace("over", "")
                .replace(".","");

        // Создаем объект аннотатора StanfordCoreNLP с нужными нам аннотаторами
        Properties props = new Properties();
        props.setProperty("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

        // Создаем объект аннотации для обработки текста
        Annotation document = new Annotation(text);

        // Запускаем аннотатор для обработки текста
        pipeline.annotate(document);
        return document;
    }
}
