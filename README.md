# 🔍 Search Engine

A full-featured web search engine built with Spring Boot that crawls websites, indexes content, and provides fast search functionality with relevance ranking.

## ✨ Features

### Core Functionality
- **🕷️ Web Crawling**: Automated website crawling with recursive link discovery
- **📚 Content Indexing**: Intelligent text analysis and lemmatization for Russian and English languages
- **🔎 Advanced Search**: Full-text search with relevance ranking and pagination
- **📊 Statistics Dashboard**: Real-time monitoring of indexing progress and database statistics
- **⚡ Single Page Indexing**: Index or re-index individual pages on demand

### Technical Highlights
- Morphological analysis using Apache Lucene
- Multi-threaded indexing with ForkJoinPool
- RESTful API architecture
- MySQL database for data persistence
- Responsive web interface

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- MySQL 8.0+
- 2GB RAM minimum
- Internet connection for crawling websites

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/mrcreate163/searchengine.git
   cd searchengine
   ```

2. **Configure the database**
   
   Create a MySQL database:
   ```sql
   CREATE DATABASE searchengine CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

3. **Update application configuration**
   
   Edit `src/main/resources/application.yaml`:
   ```yaml
   spring:
     datasource:
       username: your_mysql_username
       password: your_mysql_password
       url: jdbc:mysql://localhost:3306/searchengine?useSSL=false&requireSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   
   indexing-settings:
     sites:
       - url: https://example.com
         name: Example Site
       - url: https://another-site.com
         name: Another Site
   ```

4. **Build the project**
   ```bash
   mvn clean package
   ```

5. **Run the application**
   ```bash
   java -jar target/SearchEngine-1.0-SNAPSHOT.jar
   ```

6. **Access the web interface**
   
   Open your browser and navigate to: `http://localhost:8080`

## 📖 Usage

### Dashboard
View real-time statistics about indexed sites, pages, and lemmas. Monitor the status of each configured site.

### Management
- **Start Indexing**: Begin crawling and indexing all configured websites
- **Stop Indexing**: Halt the indexing process at any time
- **Add/Update Page**: Index a single page by providing its URL
- **Add Site**: Dynamically add a new site to the configuration via API
- **Remove Site**: Remove a site from the configuration via API

### Search
Enter your search query and optionally select a specific site to search within. Results are ranked by relevance with highlighted keywords in snippets.

## 🏗️ Architecture

### Backend Structure
```
searchengine/
├── controllers/          # REST API endpoints
├── services/            # Business logic
│   ├── IndexingService  # Website crawling and indexing
│   ├── SearchService    # Search functionality
│   ├── StatisticsService# Statistics aggregation
│   └── LemmatizationService # Text analysis
├── model/               # JPA entities
├── repository/          # Data access layer
└── dto/                 # Data transfer objects
```

### Database Schema
- **site**: Stores website information and indexing status
- **page**: Contains crawled pages with HTTP status codes
- **lemma**: Stores normalized words with frequency data
- **index**: Links pages to lemmas with rank scores

## 🔧 API Endpoints

### Statistics
```
GET /api/statistics
```
Returns comprehensive statistics about indexed sites, pages, and lemmas.

### Site Management
```
POST /api/site
```
Add a new site to the indexing configuration dynamically.

**Request Body:**
```json
{
  "url": "https://example.com",
  "name": "Example Site"
}
```

**Response:**
```json
{
  "result": true
}
```

```
DELETE /api/site?url={siteUrl}
```
Remove a site from the indexing configuration.

**Response:**
```json
{
  "result": true
}
```

**Error Response:**
```json
{
  "result": false,
  "error": "Error message description"
}
```

### Indexing Management
```
GET /api/startIndexing
GET /api/stopIndexing
POST /api/indexPage?url={pageUrl}
```

### Search
```
GET /api/search?query={searchQuery}&site={siteUrl}&offset={offset}&limit={limit}
```

**Parameters:**
- `query` (required): Search terms
- `site` (optional): Specific site URL to search within
- `offset` (optional): Pagination offset (default: 0)
- `limit` (optional): Results per page (default: 20)

## 🧪 Testing

The project includes comprehensive unit tests for all service classes.

Run tests with:
```bash
mvn test
```

**Test Coverage:**
- ✅ LemmatizationService: 12 tests
- ✅ IndexingService: 12 tests  
- ✅ SiteManagement: 15 tests
- ✅ SearchService: 7 tests
- ✅ StatisticsService: 6 tests

**Total: 52 tests, all passing** ✅

## 🛠️ Technologies Used

### Backend
- **Spring Boot 2.7.1** - Application framework
- **Spring Data JPA** - Data persistence
- **Hibernate** - ORM
- **MySQL** - Database
- **Apache Lucene Morphology** - Text analysis
- **Jsoup** - HTML parsing

### Frontend
- **Thymeleaf** - Template engine
- **jQuery** - DOM manipulation
- **HTML5/CSS3** - UI structure and styling

### Testing
- **JUnit 5** - Testing framework
- **Mockito** - Mocking framework
- **H2 Database** - In-memory database for tests

## ⚙️ Configuration

### Indexing Settings

Configure sites to index in `application.yaml`:

```yaml
indexing-settings:
  sites:
    - url: https://example.com
      name: Example Site
    - url: https://another-example.com
      name: Another Example
```

### Database Settings

```yaml
spring:
  datasource:
    username: username
    password: password
    url: jdbc:mysql://localhost:3306/searchengine
  jpa:
    hibernate:
      ddl-auto: update  # Use 'validate' in production
    show-sql: false     # Enable for debugging
```

### Server Configuration

```yaml
server:
  port: 8080  # Change to your preferred port
```

## 🐛 Troubleshooting

### Common Issues

**Database Connection Error**
- Verify MySQL is running
- Check credentials in `application.yaml`
- Ensure database exists

**Out of Memory Error**
- Increase JVM heap size: `java -Xmx2g -jar app.jar`
- Reduce the number of sites being indexed simultaneously

**Indexing Fails**
- Check site URL is accessible
- Verify robots.txt allows crawling
- Review error messages in the Dashboard

## 📝 Development

### Building from Source

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Package
mvn package

# Run locally
mvn spring-boot:run
```

### Code Style

The project follows standard Java coding conventions:
- Use meaningful variable names
- Add comments for complex logic
- Write tests for new features
- Keep methods focused and small

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👤 Author

**mrcreate163**
- GitHub: [@mrcreate163](https://github.com/mrcreate163)

## 🙏 Acknowledgments

- Apache Lucene for morphological analysis libraries
- Spring Boot team for the excellent framework
- Contributors and users of this project

## 📮 Contact & Support

If you have questions or need help:
- Open an issue on GitHub
- Check existing issues for solutions
- Review the troubleshooting section

---

⭐ If you find this project useful, please consider giving it a star!
