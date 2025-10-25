package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.site.SiteRequest;
import searchengine.dto.site.SiteResponse;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.indexing.SiteMapBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesList;
    private final LemmatizationService lemmatizationService;
    private final IndexRepository indexRepository;
    private final LemmaRepository lemmaRepository;
    private volatile ForkJoinPool forkJoinPool;


    @Override
    public IndexingResponse startIndexing() {
        if (isIndexingRunning()) {
            return new IndexingResponse(false, "Индексация уже запущена");
        }

        // Безопасная очистка БД: сначала индексы, затем страницы, затем леммы и сайты
        try {
            indexRepository.deleteAll();
            pageRepository.deleteAll();
            lemmaRepository.deleteAll();
            siteRepository.deleteAll();
        } catch (Exception e) {
            log.error("Ошибка при очистке БД перед индексацией", e);
            return new IndexingResponse(false, "Не удалось очистить БД перед индексацией: " + e.getMessage());
        }

        SiteMapBuilder.resetIndexing();

        forkJoinPool = new ForkJoinPool();

        for (Site configSite : sitesList.getSites()) {
            searchengine.model.Site siteEntity = new searchengine.model.Site();
            siteEntity.setUrl(configSite.getUrl());
            siteEntity.setName(configSite.getName());
            siteEntity.setStatus(Status.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            SiteMapBuilder siteMapBuilder = new SiteMapBuilder(
                    configSite.getUrl(),
                    siteEntity,
                    siteRepository,
                    pageRepository,
                    lemmatizationService,
                    lemmaRepository,
                    indexRepository
            );

            forkJoinPool.execute(siteMapBuilder);
        }

        new Thread(this::monitorIndexing).start();

        return new IndexingResponse(true);
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!isIndexingRunning()) {
            return new IndexingResponse(false, "Индексация не запущена");
        }

        SiteMapBuilder.stopIndexing();
        if (forkJoinPool != null) {
            forkJoinPool.shutdownNow();
        }

        siteRepository.findAll().forEach(site -> {
            if (site.getStatus().equals(Status.INDEXING)) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
            }
        });

        return new IndexingResponse(true);
    }

    @Override
    public IndexingResponse indexPage(String url) {
        // Проверяем принадлежит ли URL к одному из сайтов в конфигурации (по host)
        Site configSite = null;
        String host;
        try {
            host = new URI(url).getHost();
        } catch (Exception e) {
            return new IndexingResponse(false, "Некорректный URL: " + url);
        }
        if (host == null) {
            return new IndexingResponse(false, "Некорректный URL: отсутствует host");
        }
        host = normalizeHost(host);

        for (Site site : sitesList.getSites()) {
            try {
                String siteHost = new URI(site.getUrl()).getHost();
                siteHost = normalizeHost(siteHost);
                if (host.equalsIgnoreCase(siteHost)) {
                    configSite = site;
                    break;
                }
            } catch (Exception ignored) { }
        }

        if (configSite == null) {
            return new IndexingResponse(false, "Данный URL находится за пределами сайтов, указанных в конфигурации");
        }

        try {
            // Получаем или создаём сайт в БД
            searchengine.model.Site siteEntity = siteRepository.findByUrl(configSite.getUrl());
            if (siteEntity == null) {
                siteEntity = new searchengine.model.Site();
                siteEntity.setUrl(configSite.getUrl());
                siteEntity.setName(configSite.getName());
                siteEntity.setStatus(Status.INDEXED);
                siteEntity.setStatusTime(LocalDateTime.now());
                siteRepository.save(siteEntity);
            }

            // Вычисляем относительный путь
            String normalizedSiteUrl = trimTrailingSlash(configSite.getUrl());
            String normalizedUrl = trimTrailingSlash(url);
            String path = normalizedUrl.replaceFirst("^" + java.util.regex.Pattern.quote(normalizedSiteUrl), "");
            if (path.isEmpty()) {
                path = "/";
            }

            Page existingPage = pageRepository.findBySiteAndPath(siteEntity, path);
            if (existingPage != null) {
                // Удаляем связанные индексы и корректируем частоты лемм
                List<Index> indices = indexRepository.findByPage(existingPage);
                for (Index index : indices) {
                    Lemma lemma = index.getLemma();
                    lemma.setFrequency(Math.max(0, lemma.getFrequency() - 1));
                    if (lemma.getFrequency() <= 0) {
                        lemmaRepository.delete(lemma);
                    } else {
                        lemmaRepository.save(lemma);
                    }
                }
                indexRepository.deleteByPage(existingPage);
                pageRepository.delete(existingPage);
            }

            // Загружаем и сохраняем страницу
            Connection.Response response = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .timeout(10000)
                    .execute();

            Document document = response.parse();
            String content = document.html();

            Page page = new Page();
            page.setSite(siteEntity);
            page.setPath(path);
            page.setCode(response.statusCode());
            page.setContent(content);
            pageRepository.save(page);

            if (response.statusCode() == 200) {
                indexPageContent(page, content, siteEntity);
            }

            return new IndexingResponse(true);
        } catch (Exception e) {
            return new IndexingResponse(false, "Ошибка при индексации страницы: " + e.getMessage());
        }
    }

    private String normalizeHost(String h) {
        if (h == null) return null;
        String lower = h.toLowerCase();
        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }

    private void indexPageContent(Page page, String content, searchengine.model.Site configSite) {
        try {
            String cleanContent = lemmatizationService.cleanHtmlContent(content);
            Map<String, Integer> lemmas = lemmatizationService.getLemmas(cleanContent);

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String lemmaWord = entry.getKey();
                Integer count = entry.getValue();

                // Ищем или создаём лемму с защитой от гонок
                Lemma lemma;
                Optional<Lemma> optionalLemma = lemmaRepository.findBySiteAndLemma(configSite, lemmaWord);
                if (optionalLemma.isPresent()) {
                    lemma = optionalLemma.get();
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    lemmaRepository.save(lemma);
                } else {
                    lemma = new Lemma();
                    lemma.setSite(configSite);
                    lemma.setLemma(lemmaWord);
                    lemma.setFrequency(1);
                    try {
                        lemmaRepository.save(lemma);
                    } catch (DataIntegrityViolationException ex) {
                        // Лемма уже вставлена параллельным потоком — перечитываем
                        lemma = lemmaRepository.findBySiteAndLemma(configSite, lemmaWord).orElseThrow(() -> ex);
                        lemma.setFrequency(lemma.getFrequency() + 1);
                        lemmaRepository.save(lemma);
                    }
                }

                // Создаем индекс, избегая дубликатов
                if (!indexRepository.existsByPageAndLemma(page, lemma)) {
                    Index index = new Index();
                    index.setPage(page);
                    index.setLemma(lemma);
                    index.setRank(count.floatValue());
                    try {
                        indexRepository.save(index);
                    } catch (DataIntegrityViolationException ex) {
                        // Индекс уже создан другим потоком — пропускаем
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при индексации содержимого страницы: " + page.getPath(), e);
        }
    }

    private String trimTrailingSlash(String value) {
        if (value == null) return null;
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private boolean isIndexingRunning() {
        return siteRepository.findAll().stream()
                .anyMatch(site -> site.getStatus().equals(Status.INDEXING));
    }

    private void monitorIndexing() {
        try {
            if (forkJoinPool != null) {
                ForkJoinPool pool = forkJoinPool;
                // Ждём пока пул будет в состоянии покоя
                while (!(pool.isQuiescent() && pool.getActiveThreadCount() == 0 &&
                        pool.getQueuedSubmissionCount() == 0 && pool.getQueuedTaskCount() == 0)) {
                    Thread.sleep(500);
                }
                pool.shutdown();

                siteRepository.findAll().forEach(site -> {
                    if (site.getStatus().equals(Status.INDEXING)) {
                        site.setStatus(Status.INDEXED);
                        site.setStatusTime(LocalDateTime.now());
                        siteRepository.save(site);
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public SiteResponse addSite(SiteRequest siteRequest) {
        if (siteRequest.getUrl() == null || siteRequest.getUrl().trim().isEmpty()) {
            return new SiteResponse(false, "URL не может быть пустым");
        }
        
        if (siteRequest.getName() == null || siteRequest.getName().trim().isEmpty()) {
            return new SiteResponse(false, "Название сайта не может быть пустым");
        }
        
        // Normalize URL
        String normalizedUrl = siteRequest.getUrl().trim();
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            return new SiteResponse(false, "URL должен начинаться с http:// или https://");
        }
        
        // Remove trailing slash for consistency
        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        
        // Check if site already exists in configuration
        for (Site site : sitesList.getSites()) {
            String existingUrl = site.getUrl().endsWith("/") ? 
                site.getUrl().substring(0, site.getUrl().length() - 1) : site.getUrl();
            if (existingUrl.equals(normalizedUrl)) {
                return new SiteResponse(false, "Сайт с таким URL уже существует в конфигурации");
            }
        }
        
        // Add site to configuration
        Site newSite = new Site();
        newSite.setUrl(normalizedUrl);
        newSite.setName(siteRequest.getName().trim());
        sitesList.addSite(newSite);
        
        log.info("Сайт добавлен в конфигурацию: {} - {}", newSite.getUrl(), newSite.getName());
        
        return new SiteResponse(true);
    }
    
    @Override
    public SiteResponse removeSite(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new SiteResponse(false, "URL не может быть пустым");
        }
        
        // Normalize URL
        String normalizedUrl = url.trim();
        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        
        // Check if indexing is running for this site
        searchengine.model.Site siteEntity = siteRepository.findByUrl(normalizedUrl);
        if (siteEntity != null && siteEntity.getStatus().equals(Status.INDEXING)) {
            return new SiteResponse(false, "Невозможно удалить сайт: индексация в процессе. Остановите индексацию перед удалением.");
        }
        
        // Remove from configuration
        boolean removed = sitesList.removeSite(normalizedUrl);
        
        if (!removed) {
            return new SiteResponse(false, "Сайт с таким URL не найден в конфигурации");
        }
        
        log.info("Сайт удален из конфигурации: {}", normalizedUrl);
        
        return new SiteResponse(true);
    }
}
