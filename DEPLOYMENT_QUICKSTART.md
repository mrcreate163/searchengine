# 📋 Краткая справка по развертыванию

Быстрый справочник для развертывания Search Engine на DigitalOcean.

## 🎯 Что было сделано для подготовки к деплою

### Добавленные файлы

1. **Dockerfile** - Контейнеризация приложения
   - Multi-stage сборка для оптимизации размера
   - Использование non-root пользователя для безопасности
   - Health check для мониторинга

2. **docker-compose.yml** - Оркестрация сервисов (обновлен)
   - MySQL 8.0 с health checks
   - Spring Boot приложение
   - Nginx reverse proxy
   - Certbot для SSL сертификатов
   - Изолированная сеть и persistent хранилище

3. **application-prod.yaml** - Production конфигурация
   - Оптимизированные настройки базы данных
   - Настройки безопасности
   - Конфигурация логирования
   - Actuator endpoints для health checks

4. **.env.example** - Шаблон переменных окружения
   - Конфигурация базы данных
   - Настройки домена и SSL
   - Сайты для индексации

5. **nginx/** - Конфигурация веб-сервера
   - `nginx.conf` - Основная конфигурация
   - `conf.d/searchengine.conf` - Конфигурация приложения
   - Reverse proxy
   - SSL/TLS настройки
   - Кэширование статики

6. **deploy.sh** - Скрипт автоматического развертывания
   - Проверка конфигурации
   - Сборка и запуск сервисов
   - Health checks

7. **setup-ssl.sh** - Скрипт настройки SSL
   - Получение Let's Encrypt сертификатов
   - Автоматическое обновление
   - Настройка HTTPS

8. **init.sql** - Инициализация базы данных
   - Создание схемы
   - Установка кодировки UTF-8

9. **DEPLOYMENT_GUIDE.md** - Полное руководство по развертыванию (на русском)
   - Пошаговая инструкция
   - Настройка DigitalOcean
   - Настройка Namecheap
   - Решение проблем

10. **DEPLOYMENT_QUICKSTART.md** - Этот файл

### Изменения в существующих файлах

1. **pom.xml**
   - Добавлен Spring Boot Maven Plugin с includeSystemScope
   - Добавлена зависимость Spring Boot Actuator для health checks
   - Настроена секция build

2. **.gitignore**
   - Добавлено исключение .env файла
   - Добавлено исключение certbot/ директории

## 🚀 Быстрый старт

### Предварительные требования
- Аккаунт DigitalOcean
- Домен на Namecheap
- SSH ключ

### 1. Создать Droplet
```bash
# Параметры:
# - OS: Ubuntu 22.04 LTS
# - Plan: Basic, 2GB RAM ($12/mo)
# - Region: Ближайший к вашим пользователям
# - Authentication: SSH keys
```

### 2. Настроить DNS
```
Type    Host    Value           TTL
A       @       YOUR_IP         Automatic
A       www     YOUR_IP         Automatic
```

### 3. Установить ПО на сервере
```bash
# Подключиться
ssh root@YOUR_IP

# Обновить систему
apt update && apt upgrade -y

# Установить Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh

# Установить Docker Compose
curl -L "https://github.com/docker/compose/releases/download/v2.23.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose

# Установить Git
apt install -y git

# Настроить firewall
ufw enable
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
```

### 4. Развернуть приложение
```bash
# Клонировать репозиторий
mkdir -p /opt/apps && cd /opt/apps
git clone https://github.com/mrcreate163/searchengine.git
cd searchengine

# Настроить конфигурацию
cp .env.example .env
nano .env  # Отредактировать параметры

# Запустить
./deploy.sh
```

### 5. Настроить SSL (после обновления DNS)
```bash
./setup-ssl.sh
```

## 📝 Шаблон конфигурации .env

```bash
# Базовые настройки
MYSQL_ROOT_PASSWORD=сложный_пароль_123
MYSQL_DATABASE=searchengine
MYSQL_USER=searchengine_user
MYSQL_PASSWORD=другой_сложный_пароль_456

SPRING_PROFILES_ACTIVE=prod

# Сайты для индексации (JSON формат)
INDEXING_SITES=[{"url":"https://www.example.com","name":"Example Site"}]

# Домен
DOMAIN_NAME=yourdomain.com
SSL_EMAIL=your-email@example.com
```

## 🔧 Основные команды

### Управление сервисами
```bash
docker-compose ps              # Статус
docker-compose logs -f         # Логи
docker-compose restart         # Перезапуск
docker-compose down            # Остановка
docker-compose up -d           # Запуск
```

### Обновление приложения
```bash
cd /opt/apps/searchengine
git pull
docker-compose build app
docker-compose up -d
```

### Бэкап базы данных
```bash
docker-compose exec mysql mysqldump -u root -p$MYSQL_ROOT_PASSWORD searchengine | gzip > backup.sql.gz
```

### Восстановление базы данных
```bash
gunzip < backup.sql.gz | docker-compose exec -T mysql mysql -u root -p$MYSQL_ROOT_PASSWORD searchengine
```

## 🔍 Проверка работы

### После развертывания
```bash
# Статус контейнеров
docker-compose ps

# Health check
curl http://localhost:8080/actuator/health

# Статистика
curl http://localhost:8080/api/statistics
```

### После настройки SSL
```bash
# Проверка HTTP -> HTTPS редиректа
curl -I http://yourdomain.com

# Проверка HTTPS
curl https://yourdomain.com/health

# Открыть в браузере
https://yourdomain.com
```

## 📊 Мониторинг

### Использование ресурсов
```bash
docker stats                   # Контейнеры
htop                          # Система
df -h                         # Диск
free -h                       # Память
```

### Логи
```bash
# Все сервисы
docker-compose logs -f

# Только приложение
docker-compose logs -f app

# Последние 100 строк
docker-compose logs --tail=100 app
```

## 🛡️ Безопасность

### Чек-лист безопасности
- ✅ Сильные пароли в .env
- ✅ Firewall настроен (UFW)
- ✅ SSH только по ключу
- ✅ SSL сертификат установлен
- ✅ Регулярные обновления системы
- ✅ Бэкапы настроены

### Рекомендации
1. Создайте отдельного пользователя (не используйте root)
2. Установите fail2ban
3. Регулярно обновляйте систему: `apt update && apt upgrade`
4. Мониторьте логи на подозрительную активность
5. Используйте сложные пароли (16+ символов)

## ⚠️ Устранение неполадок

### Приложение не запускается
```bash
docker-compose logs app        # Проверить логи
docker-compose ps             # Проверить статус
docker-compose restart app    # Перезапустить
```

### База данных недоступна
```bash
docker-compose logs mysql     # Проверить логи
docker-compose restart mysql  # Перезапустить
```

### SSL не работает
```bash
# Проверить DNS
ping yourdomain.com

# Проверить порты
ufw status

# Посмотреть логи certbot
docker-compose logs certbot

# Попробовать снова
./setup-ssl.sh
```

### Недостаточно памяти
```bash
# Добавить swap
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

## 📚 Архитектура деплоя

```
Internet
    ↓
DigitalOcean Droplet (Ubuntu 22.04)
    ↓
UFW Firewall (ports 22, 80, 443)
    ↓
Docker Network (searchengine-network)
    ├── Nginx (reverse proxy + SSL)
    │   ↓
    ├── Spring Boot App (port 8080)
    │   ↓
    ├── MySQL 8.0 (port 3306)
    │   └── Persistent Volume (mysql_data)
    │
    └── Certbot (SSL certificates)
        └── Persistent Volume (certbot/conf)
```

## 💰 Стоимость

### Месячные расходы
- **DigitalOcean Droplet (2GB):** $12/месяц
- **Домен (Namecheap):** ~$1/месяц (амортизация)
- **SSL сертификат:** $0 (Let's Encrypt)
- **Общий траффик:** Включен в Droplet

**Итого:** ~$13/месяц

### Экономия
- Используйте реферальный код DigitalOcean для $200 кредита
- Можно начать с 1GB Droplet ($6/месяц) и увеличить позже

## 🔗 Полезные ссылки

- [Полное руководство по развертыванию](DEPLOYMENT_GUIDE.md)
- [DigitalOcean Documentation](https://docs.digitalocean.com/)
- [Docker Compose Documentation](https://docs.docker.com/compose/)
- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [Nginx Documentation](https://nginx.org/en/docs/)

## 📞 Поддержка

Если что-то не работает:
1. Проверьте [Полное руководство](DEPLOYMENT_GUIDE.md) - раздел "Решение проблем"
2. Проверьте логи: `docker-compose logs -f`
3. Откройте Issue на GitHub
4. Проверьте существующие Issues

---

**Создано для проекта:** [Search Engine](https://github.com/mrcreate163/searchengine)
**Автор:** mrcreate163
**Лицензия:** MIT
