package searchengine.services.indexing;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.dao.DataIntegrityViolationException;
import searchengine.model.*;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.LemmatizationService;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class SiteMapBuilder extends RecursiveAction {

    private String url;
    private Site site;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmatizationService lemmatizationService;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private static final Set<String> allLinks = new CopyOnWriteArraySet<>();
    private static volatile boolean isIndexingStopped = false;



    public SiteMapBuilder(String url, Site site,
                          SiteRepository siteRepository,
                          PageRepository pageRepository,
                          LemmatizationService lemmatizationService,
                          LemmaRepository lemmaRepository,
                          IndexRepository indexRepository) {
        this.url = url;
        this.site = site;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmatizationService = lemmatizationService;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }


    @Override
    protected void compute() {
        if (isIndexingStopped) {
            return;
        }
        try {
            Thread.sleep(200);

            String normalizedUrl = trimTrailingSlash(url);
            String normalizedSiteUrl = trimTrailingSlash(site.getUrl());
            String path = normalizedUrl.replaceFirst("^" + java.util.regex.Pattern.quote(normalizedSiteUrl), "");
            if (path.isEmpty()) {
                path = "/";
            }

            // Канонический ключ для дедупликации: siteUrl + path
            String canonicalKey = normalizedSiteUrl + path;
            if (!allLinks.add(canonicalKey)) {
                return;
            }

            Connection.Response response = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .timeout(10000)
                    .execute();

            Document document = response.parse();
            String content = document.html();

            // Сохраняем или читаем существующую страницу (защита от гонок)
            Page page = pageRepository.findBySiteAndPath(site, path);
            if (page == null) {
                page = new Page();
                page.setSite(site);
                page.setPath(path);
            }
            page.setCode(response.statusCode());
            page.setContent(content);
            try {
                page = pageRepository.save(page);
            } catch (DataIntegrityViolationException ex) {
                // Страница уже создана параллельно – перечитаем
                page = pageRepository.findBySiteAndPath(site, path);
            }

            // Обновляем время последней активности
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

            // Ищем ссылки и индексируем только успешные страницы
            if (response.statusCode() == 200) {
                indexPageContent(page, content);

                Set<SiteMapBuilder> taskList = new HashSet<>();
                Elements links = document.select("a[href]");

                for (Element link : links) {
                    String childUrl = link.absUrl("href");
                    if (isValidUrl(childUrl, site.getUrl())) {
                        SiteMapBuilder task = new SiteMapBuilder(
                                childUrl,
                                site,
                                siteRepository,
                                pageRepository,
                                lemmatizationService,
                                lemmaRepository,
                                indexRepository
                        );
                        task.fork();
                        taskList.add(task);
                    }
                }

                for (SiteMapBuilder task : taskList) {
                    task.join();
                }
            }
        } catch (Exception e) {
            handleError(e);
        }
    }

    private void indexPageContent(Page page, String content) {
        try {
            String cleanContent = lemmatizationService.cleanHtmlContent(content);
            Map<String, Integer> lemmas = lemmatizationService.getLemmas(cleanContent);

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaWord = entry.getKey();
                Integer count = entry.getValue();

                // Ищем или создаем лемму с защитой от гонок
                Lemma lemma;
                Optional<Lemma> optionalLemma = lemmaRepository.findBySiteAndLemma(site, lemmaWord);
                if (optionalLemma.isPresent()) {
                    lemma = optionalLemma.get();
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    lemmaRepository.save(lemma);
                } else {
                    lemma = new Lemma();
                    lemma.setSite(site);
                    lemma.setLemma(lemmaWord);
                    lemma.setFrequency(1);
                    try {
                        lemmaRepository.save(lemma);
                    } catch (DataIntegrityViolationException ex) {
                        // Уже вставлена параллельным потоком – перечитываем
                        lemma = lemmaRepository.findBySiteAndLemma(site, lemmaWord).orElseThrow(() -> ex);
                        lemma.setFrequency(lemma.getFrequency() + 1);
                        lemmaRepository.save(lemma);
                    }
                }

                // Создаём индекс, избегая дубликатов
                if (!indexRepository.existsByPageAndLemma(page, lemma)) {
                    Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank(count.floatValue());
                    try {
                        indexRepository.save(index);
                    } catch (DataIntegrityViolationException ex) {
                        // Индекс уже создан другим потоком – пропускаем
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при индексации содержимого страницы: " + page.getPath(), e);
        }
    }

    private boolean isValidUrl(String candidateUrl, String siteUrl) {
        try {
            URI c = new URI(candidateUrl);
            URI s = new URI(siteUrl);
            if (c.getHost() == null || s.getHost() == null) return false;
            boolean sameHost = c.getHost().equalsIgnoreCase(s.getHost());
            if (!sameHost) return false;
            String path = c.getPath() == null ? "/" : c.getPath();
            // фильтр якорей и бинарных ресурсов
            return (c.getFragment() == null)
                    && !path.matches(".*(\\.(jpg|jpeg|png|gif|bmp|pdf|doc|docx|xls|xlsx|ppt|pptx|mp3|mp4|avi|mov|wmv|zip|rar))$")
                    && candidateUrl.length() < 2048;
        } catch (Exception e) {
            return false;
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return null;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private void handleError(Exception e) {
        site.setStatus(Status.FAILED);
        site.setLastError(e.getMessage());
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    public static void stopIndexing() {
        isIndexingStopped = true;
    }

    public static void resetIndexing() {
        isIndexingStopped = false;
        allLinks.clear();
    }
}
