-- Скрипт для очистки дубликатов в таблице site

-- 1. Сначала удаляем связанные страницы для дубликатов
DELETE p FROM page p
INNER JOIN site s ON p.site_id = s.id
WHERE s.id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(id) as min_id
        FROM site
        GROUP BY url
    ) AS keep_sites
);

-- 2. Удаляем дубликаты сайтов, оставляя только запись с минимальным ID
DELETE s FROM site s
WHERE s.id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(id) as min_id
        FROM site
        GROUP BY url
    ) AS keep_sites
);

-- 3. Добавляем уникальное ограничение на поле url (если его еще нет)
ALTER TABLE site ADD UNIQUE KEY unique_url (url);

