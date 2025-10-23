package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmatizationService lemmatizationService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final SitesList sitesList;

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return new SearchResponse(false, "Задан пустой поисковый запрос");
        }

        //Получаем леммы из поискового запроса
        Set<String> queryLemmas = lemmatizationService.getLemmaSet(query.toLowerCase());
        if (queryLemmas.isEmpty()) {
            return new SearchResponse(false, "По запросу '" + query + "' ничего не найдено");
        }

        //Определяем сайты для поиска
        List<Site> siteToSearch = getSitesToSearch(site);
        if (siteToSearch.isEmpty()) {
            return new SearchResponse(false, "Сайт для поиска не найден");
        }

        //Выполняем поиск по выбранным сайтам
        Map<Page, Float> pageRelevanceMap = performSearch(queryLemmas, siteToSearch);

        if (pageRelevanceMap.isEmpty()) {
            return new SearchResponse(true, 0, new ArrayList<>());
        }

        //Сортируем страницы по релевантности и применяем пагинацию
        List<Map.Entry<Page, Float>> sortedResults = pageRelevanceMap.entrySet()
                .stream()
                .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
                .collect(Collectors.toList());

        //Применяем пагинацию
        int total = sortedResults.size();
        int fromIndex = Math.min(offset, total);
        int toIndex = Math.min(offset + limit, total);

        if (fromIndex >= total) {
            return new SearchResponse(true, total, new ArrayList<>());
        }

        List<Map.Entry<Page, Float>> paginatedResult = sortedResults.subList(fromIndex, toIndex);

        //Формируем ответ
        List<SearchData> searchResults = paginatedResult.stream()
                .map(entry -> createSearchData(entry.getKey(), entry.getValue(), queryLemmas))
                .collect(Collectors.toList());

        return new SearchResponse(true, total, searchResults);
    }

    private List<searchengine.model.Site> getSitesToSearch(String siteUrl) {
        if (siteUrl == null || siteUrl.trim().isEmpty()) {
            return siteRepository.findAll();
        }

        Site site = siteRepository.findByUrl(siteUrl);
        return site != null ? List.of(site) : Collections.emptyList();
    }

    private Map<Page, Float> performSearch(Set<String> queryLemmas, List<Site> sites) {
        Map<Page, Float> pageRelevanceMap = new HashMap<>();
        List<String> lemmasList = new ArrayList<>(queryLemmas);

        for (Site site : sites) {
            //Получаем леммы из БД для текущего сайта
            List<Lemma> lemmasFromDb = lemmaRepository.findLemmasBySiteAndLemmaIn(site, lemmasList);

            if (lemmasFromDb.size() != queryLemmas.size()) {
                continue; //Не все леммы найдены на этом сайте
            }

            //Фильтруем слишком частотные леммы
            List<Lemma> filteredLemmas = filterFrequentLemmas(lemmasFromDb, site);
            if (filteredLemmas.isEmpty()) {
                continue; //Все леммы отфильтрованы
            }

            //Находим страницы, содержащие все леммы
            Map<Page, Float> siteResults = findPagesWithAllLemmas(filteredLemmas);
            pageRelevanceMap.putAll(siteResults);
        }

        return normalizeRelevance(pageRelevanceMap);

    }

    private List<Lemma> filterFrequentLemmas(List<Lemma> lemmas, Site site) {
        long totalLemmas = lemmaRepository.countTotalLemmasBySite(site);
        return lemmas.stream()
                .filter(lemma -> {
                    double frequency = (double) lemma.getFrequency() / totalLemmas;
                    return frequency < 0.8; //Фильтрация лемм с частотой более 80%
                })
                .collect(Collectors.toList());
    }

    private Map<Page, Float> findPagesWithAllLemmas(List<Lemma> lemmas) {
        if (lemmas.isEmpty()) {
            return new HashMap<>();
        }

        //Сортируем леммы по частоте (от редких к частым)
        lemmas.sort(Comparator.comparingInt(Lemma::getFrequency));

        //Начинаем с поиска страниц по самой редкой лемме
        List<Index> indexes = indexRepository.findByLemmaIn(List.of(lemmas.get(0)));
        Map<Page, List<Index>> pageIndexes = indexes.stream()
                .collect(Collectors.groupingBy(Index::getPage));

        //Фильтруем страницы, которые содержат все леммы
        Map<Page, Float> result = new HashMap<>();

        for (Map.Entry<Page, List<Index>> entry : pageIndexes.entrySet()) {
            Page page = entry.getKey();

            //Проверяем есть ли все леммы на этой странице
            List<Index> pageAllIndexes = indexRepository.findByPageAndLemmaIn(page, lemmas);

            if (pageAllIndexes.size() == lemmas.size()) {
                //Вычисляем абсолютную релевантность
                float absoluteRelevance = (float) pageAllIndexes.stream()
                        .mapToDouble(Index::getRank)
                        .sum();

                result.put(page, absoluteRelevance);
            }
        }

        return result;
    }

    private Map<Page, Float> normalizeRelevance(Map<Page, Float> pageRelevanceMap) {
        if (pageRelevanceMap.isEmpty()) {
            return pageRelevanceMap;
        }

        //Находим максимальную абсолютную релевантность
        float maxRelevance = pageRelevanceMap.values().stream()
                .max(Float::compare)
                .orElse(1.0f);

        //Нормализуем все значения релевантности
        Map<Page, Float> normalizedMap = new HashMap<>();
        for (Map.Entry<Page, Float> entry : pageRelevanceMap.entrySet()) {
            float normalizedRelevance = entry.getValue() / maxRelevance;
            normalizedMap.put(entry.getKey(), normalizedRelevance);
        }

        return normalizedMap;
    }

    private SearchData createSearchData(Page page, float relevance, Set<String> queryLemmas) {
        SearchData searchData = new SearchData();

        //Находим название сайта из конфигурации
        String siteName = sitesList.getSites().stream()
                .filter(s -> s.getUrl().equals(page.getSite().getUrl()))
                .map(searchengine.config.Site::getName)
                .findFirst()
                .orElse(page.getSite().getName());

        searchData.setSite(page.getSite().getUrl());
        searchData.setSiteName(siteName);
        searchData.setUri(page.getPath());
        searchData.setRelevance(relevance);

        //Извлекаем заголовок и создаём сниппет
        String cleanContent = lemmatizationService.cleanHtmlContent(page.getContent());
        searchData.setTitle(extractTitle(page.getContent()));
        searchData.setSnippet(createSnippet(cleanContent, queryLemmas));

        return searchData;
    }

    private String extractTitle(String htmlContent) {
        try {
            int titleStar = htmlContent.toLowerCase().indexOf("<title>");
            int titleEnd = htmlContent.toLowerCase().indexOf("</title>");

            if (titleStar != -1 && titleEnd != -1 && titleStar < titleEnd) {
                return htmlContent.substring(titleStar + 7, titleEnd).trim();
            }
        } catch (Exception e) {
            log.error("Ошибка при извлечении заголовка: " + e.getMessage());
        }
        return "Нет заголовка";
    }

    private String createSnippet(String content, Set<String> queryLemmas) {
        String[] sentences = content.split("[.!?]+");
        List<String> relevantSentences = new ArrayList<>();

        //Ищем предложения, содержащие леммы из запроса
        for (String sentence : sentences) {
            if (sentence.trim().length() < 10) continue;

            Set<String> sentenceLemmas = lemmatizationService.getLemmaSet(sentence.toLowerCase());
            boolean hasQuery = sentenceLemmas.stream()
                    .anyMatch(queryLemmas::contains);

            if (hasQuery) {
                relevantSentences.add(sentence.trim());
                if (relevantSentences.size() >= 3) break; //Ограничиваем количество предложений в сниппете
            }
        }

        // Если не нашли релевантные предложения, берём первые предложения
        if (relevantSentences.isEmpty()) {
            for (int i = 0; i < Math.min(2, sentences.length); i++) {
                if (sentences[i].trim().length() > 10) {
                    relevantSentences.add(sentences[i].trim());
                }
            }
        }

        String snippet = String.join(". ", relevantSentences);

        //Выделяем найденные слова
        snippet = highlightQueryWords(snippet, queryLemmas);

        //Обрезаем до 200 символов
        if (snippet.length() > 200) {
            snippet = snippet.substring(0, 197) + "...";
        }

        return snippet;
    }

    private String highlightQueryWords(String text, Set<String> queryLemmas) {
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String cleanWord = word.replaceAll("[^а-яё\\w]", "").toLowerCase();

            Set<String> wordLemmas = lemmatizationService.getLemmaSet(cleanWord);
            boolean shouldHighlight = wordLemmas.stream()
                    .anyMatch(queryLemmas::contains);

            if (shouldHighlight && !cleanWord.isEmpty()) {
                result.append("<b>").append(word).append("</b>");
            } else {
                result.append(word);
            }

            if (i < words.length - 1) {
                result.append(" ");
            }
        }
        return result.toString();
    }


}
