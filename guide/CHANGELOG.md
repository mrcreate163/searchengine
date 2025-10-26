# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-01-23

### Added
- Initial release of Search Engine project
- Web crawling functionality with recursive link discovery
- Content indexing with lemmatization for Russian and English
- Full-text search with relevance ranking
- Statistics dashboard for monitoring indexing progress
- Single page indexing capability
- RESTful API with 5 endpoints:
  - `/api/statistics` - Get indexing statistics
  - `/api/startIndexing` - Start full site indexing
  - `/api/stopIndexing` - Stop indexing process
  - `/api/indexPage` - Index single page
  - `/api/search` - Search indexed content
- Responsive web interface with three main sections:
  - Dashboard for statistics
  - Management for indexing control
  - Search for querying indexed content
- Comprehensive test suite with 36 unit tests
- Professional documentation:
  - README with setup and usage instructions
  - CONTRIBUTING guidelines
  - MIT License
  - This CHANGELOG

### Technical Details
- Built with Spring Boot 2.7.1
- MySQL database for persistence
- Apache Lucene for morphological analysis
- Jsoup for HTML parsing
- ForkJoinPool for multi-threaded crawling
- Thymeleaf + jQuery frontend

### Security
- No known vulnerabilities
- CodeQL security scan passed
- Proper input validation on all endpoints
- SQL injection prevention through JPA

### Documentation
- Complete API documentation in README
- Installation and configuration guide
- Troubleshooting section
- Contributing guidelines
- Code examples

## [Unreleased]

### Planned Features
- Add support for more languages
- Implement search result caching
- Add search history
- Export/import functionality for search results
- Admin panel for configuration
- Docker support
- API rate limiting
- Advanced search filters
- Search suggestions/autocomplete

---

For more information, see the [README](../README.md).
