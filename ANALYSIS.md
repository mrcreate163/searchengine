# Анализ проекта Search Engine и исправление найденных проблем

## Основная проблема (КРИТИЧЕСКАЯ)

### Проблема с эндпоинтом `/api/indexPage`

**Симптом**: При вызове эндпоинта `/api/indexPage` всегда возвращалась ошибка:
```
"Данный URL находится за пределами сайтов, указанных в конфигурации"
```

**Причина**: В файле `application.yaml` отсутствовал протокол в URL одного из сайтов:
```yaml
- url: httpbin.org  # ОШИБКА: отсутствует протокол
```

**Решение**: Добавлен протокол `https://`:
```yaml
- url: https://httpbin.org  # ИСПРАВЛЕНО
```

**Объяснение**: Метод `indexPage()` в `IndexingServiceImpl.java` проверяет, начинается ли переданный URL с URL сайта из конфигурации с помощью `url.startsWith(site.getUrl())`. Если пользователь передаёт полный URL (например, `https://httpbin.org/status/200`), а в конфигурации указан `httpbin.org` без протокола, проверка всегда будет провалена.

---

## Критические ошибки, приводящие к сбоям

### 1. Гонка данных (Race Condition) в SiteMapBuilder

**Локация**: `SiteMapBuilder.java`, строки 71-73

**Проблема**: URL проверялся на наличие в множестве `allLinks`, но никогда не добавлялся в это множество:
```java
if (allLinks.contains(url)) {
    return;
}
// URL никогда не добавлялся в allLinks!
```

**Последствия**: 
- Одна и та же страница могла обрабатываться многократно
- Дублирование данных в базе
- Бесконечные циклы при наличии перекрёстных ссылок
- Чрезмерная нагрузка на целевые сайты

**Решение**: Использована атомарная операция `add()`, которая возвращает `false`, если элемент уже был в множестве:
```java
if (!allLinks.add(url)) {
    return;
}
```

### 2. Неправильное управление частотой лемм при переиндексации

**Локация**: `IndexingServiceImpl.java`, метод `indexPage()`, строки 137-141

**Проблема**: При переиндексации существующей страницы удалялись индексы и сама страница, но частота связанных лемм не уменьшалась:
```java
Page existingPage = pageRepository.findBySiteAndPath(siteEntity, path);
if (existingPage != null) {
    indexRepository.deleteByPage(existingPage);  // Удаление индексов
    pageRepository.delete(existingPage);         // Удаление страницы
    // Частота лемм не обновлялась!
}
```

**Последствия**:
- Неверная статистика частоты лемм
- Накопление "мусорных" лемм с завышенной частотой
- Некорректная работа поискового ранжирования

**Решение**: Добавлена логика декремента частоты и удаления лемм с нулевой частотой:
```java
Page existingPage = pageRepository.findBySiteAndPath(siteEntity, path);
if (existingPage != null) {
    List<Index> indices = indexRepository.findByPage(existingPage);
    
    for (Index index : indices) {
        Lemma lemma = index.getLemma();
        lemma.setFrequency(lemma.getFrequency() - 1);
        if (lemma.getFrequency() <= 0) {
            lemmaRepository.delete(lemma);
        } else {
            lemmaRepository.save(lemma);
        }
    }
    
    indexRepository.deleteByPage(existingPage);
    pageRepository.delete(existingPage);
}
```

### 3. Отсутствующие аннотации для пользовательских методов удаления

**Локация**: `IndexRepository.java`

**Проблема**: Интерфейс `IndexRepository` расширял `CrudRepository` и имел пользовательский метод удаления `deleteByPage()` без необходимых аннотаций:
```java
public interface IndexRepository extends CrudRepository<Index, Integer> {
    void deleteByPage(Page page);  // Отсутствуют @Modifying и @Transactional
}
```

**Последствия**:
- Возможные ошибки транзакций при выполнении операций удаления
- Нарушение ACID свойств при удалении связанных записей

**Решение**: 
- Изменён базовый интерфейс на `JpaRepository`
- Добавлены аннотации `@Modifying` и `@Transactional`
- Добавлена аннотация `@Repository`
```java
@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {
    List<Index> findByPage(Page page);
    
    @Modifying
    @Transactional
    void deleteByPage(Page page);
}
```

### 4. Некорректная обработка регулярных выражений в LemmatizationService

**Локация**: `LemmatizationService.java`, метод `arrayContainsRussianWords()`, строка 116

**Проблема**: Использовался метод `replace()` вместо `replaceAll()` для обработки регулярного выражения:
```java
private String[] arrayContainsRussianWords(String text) {
    return text.toLowerCase(Locale.ROOT)
            .replace("([^а-я\\s])", " ")  // ОШИБКА: replace() не работает с regex
            .trim()
            .split("\\s+");
}
```

**Последствия**:
- Метод искал литеральную строку `"([^а-я\\s])"` вместо применения паттерна
- Не удалялись специальные символы и цифры
- Неправильная лемматизация текста
- Некорректный анализ содержимого страниц

**Решение**: Изменён на `replaceAll()` с улучшенным регулярным выражением:
```java
private String[] arrayContainsRussianWords(String text) {
    return text.toLowerCase(Locale.ROOT)
            .replaceAll("[^а-яёa-z\\s]", " ")  // Теперь работает корректно
            .trim()
            .split("\\s+");
}
```

---

## Проблемы нормализации URL

### 5. Некорректная обработка завершающих слешей в URL

**Локация**: `IndexingServiceImpl.java` и `SiteMapBuilder.java`

**Проблема**: При сравнении URL не учитывались завершающие слеши:
- Конфигурация: `https://example.com/`
- Входной URL: `https://example.com/page`
- Извлечённый путь: `/page` (корректно)

НО:
- Конфигурация: `https://example.com`
- Входной URL: `https://example.com/page`
- Извлечённый путь: `/page` (корректно)

А также:
- Конфигурация: `https://example.com`
- Входной URL: `https://example.com/`
- Извлечённый путь: `/` после replace будет пустым

**Последствия**:
- Непредсказуемое извлечение пути страницы
- Возможные ошибки при поиске существующих страниц

**Решение**: Добавлена нормализация URL перед сравнением:
```java
String normalizedUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
String normalizedSiteUrl = site.getUrl().endsWith("/") ? 
    site.getUrl().substring(0, site.getUrl().length() - 1) : site.getUrl();

if (normalizedUrl.startsWith(normalizedSiteUrl)) {
    // ...
}

String path = normalizedUrl.replace(normalizedSiteUrl, "");
if (path.isEmpty()) {
    path = "/";
}
```

---

## Проблемы целостности данных

### 6. Отсутствие уникальных ограничений в базе данных

**Проблема**: В моделях `Page`, `Lemma` и `Index` отсутствовали уникальные ограничения на комбинации полей, хотя логика приложения предполагает их уникальность.

#### Page.java
**Проблема**: Нет ограничения на уникальность комбинации (site_id, path)
```java
@Table(name = "page", indexes = {@Index(name = "path_index", columnList = "path")})
```

**Последствия**: Возможность создания дубликатов страниц для одного сайта

**Решение**:
```java
@Table(name = "page", 
       indexes = {@Index(name = "path_index", columnList = "path")},
       uniqueConstraints = {@UniqueConstraint(name = "site_path_unique", 
                                              columnNames = {"site_id", "path"})})
```

#### Lemma.java
**Проблема**: Нет ограничения на уникальность комбинации (site_id, lemma)
```java
@Table(name = "lemma")
```

**Последствия**: Возможность создания дубликатов лемм для одного сайта

**Решение**:
```java
@Table(name = "lemma",
       uniqueConstraints = {@UniqueConstraint(name = "site_lemma_unique", 
                                              columnNames = {"site_id", "lemma"})})
```

#### Index.java
**Проблема**: Нет ограничения на уникальность комбинации (page_id, lemma_id)
```java
@Table(name = "`index`")
```

**Последствия**: Возможность создания дубликатов индексных записей

**Решение**:
```java
@Table(name = "`index`",
       uniqueConstraints = {@UniqueConstraint(name = "page_lemma_unique", 
                                              columnNames = {"page_id", "lemma_id"})})
```

---

## Конфигурация зависимостей

### 7. Отсутствующие зависимости Lucene Morphology в pom.xml

**Проблема**: Библиотеки морфологического анализа находились в папке `library`, но не были подключены в `pom.xml`

**Последствия**: 
- Проект не собирался (`mvn compile` завершался с ошибками)
- Невозможность запустить приложение

**Решение**: Добавлены локальные зависимости с использованием `systemPath`:
```xml
<dependency>
    <groupId>org.apache.lucene.morphology</groupId>
    <artifactId>morph</artifactId>
    <version>1.5</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/library/morph/1.5/morph-1.5.jar</systemPath>
</dependency>
<dependency>
    <groupId>org.apache.lucene.morphology</groupId>
    <artifactId>russian</artifactId>
    <version>1.5</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/library/russian/1.5/russian-1.5.jar</systemPath>
</dependency>
<dependency>
    <groupId>org.apache.lucene.morphology</groupId>
    <artifactId>english</artifactId>
    <version>1.5</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/library/english/1.5/english-1.5.jar</systemPath>
</dependency>
<dependency>
    <groupId>org.apache.lucene.morphology</groupId>
    <artifactId>dictionary-reader</artifactId>
    <version>1.5</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/library/dictionary-reader/1.5/dictionary-reader-1.5.jar</systemPath>
</dependency>
```

---

## Резюме

### Исправлено критических проблем: 7

1. ✅ Отсутствие протокола в URL конфигурации (основная проблема)
2. ✅ Race condition при обработке URL в SiteMapBuilder
3. ✅ Некорректное управление частотой лемм при переиндексации
4. ✅ Отсутствие транзакционных аннотаций в IndexRepository
5. ✅ Некорректная обработка регулярных выражений в LemmatizationService
6. ✅ Проблемы с нормализацией URL и завершающими слешами
7. ✅ Отсутствие уникальных ограничений в моделях БД

### Добавлено улучшений: 4

1. ✅ Уникальные ограничения на уровне БД (Page, Lemma, Index)
2. ✅ Нормализация URL для консистентности
3. ✅ Правильная обработка частоты лемм
4. ✅ Конфигурация локальных библиотек в pom.xml

### Состояние проекта

- ✅ Проект успешно компилируется (`mvn clean compile`)
- ✅ Эндпоинт `/api/indexPage` теперь работает корректно
- ✅ Улучшена целостность данных
- ✅ Устранены race conditions
- ✅ Исправлены ошибки в логике лемматизации

### Рекомендации для дальнейшего развития

1. **Тестирование**: Добавить unit и integration тесты для всех критических методов
2. **Логирование**: Улучшить логирование в методах индексации для отладки
3. **Обработка ошибок**: Добавить более детальную обработку исключений
4. **Производительность**: Рассмотреть возможность пакетной обработки при индексации
5. **Конфигурация**: Вынести настройки таймаутов и user-agent в конфигурацию
6. **Миграции БД**: Использовать Flyway или Liquibase для управления схемой БД
