# Contributing to Search Engine

First off, thank you for considering contributing to Search Engine! It's people like you that make this project better.

## Code of Conduct

This project and everyone participating in it is governed by respect and professionalism. Please be kind and considerate in your interactions.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When you create a bug report, include as many details as possible:

**Bug Report Template:**
- **Description**: Clear and concise description of the bug
- **Steps to Reproduce**: Detailed steps to reproduce the issue
- **Expected Behavior**: What you expected to happen
- **Actual Behavior**: What actually happened
- **Environment**: 
  - OS version
  - Java version
  - MySQL version
  - Browser (if frontend issue)
- **Logs**: Relevant log output or error messages
- **Screenshots**: If applicable

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

- **Clear Title**: Use a clear and descriptive title
- **Detailed Description**: Provide a detailed description of the suggested enhancement
- **Use Cases**: Explain why this enhancement would be useful
- **Possible Implementation**: If you have ideas about how to implement it

### Pull Requests

1. **Fork the Repository**
   ```bash
   git clone https://github.com/mrcreate163/searchengine.git
   cd searchengine
   ```

2. **Create a Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make Your Changes**
   - Write clean, maintainable code
   - Follow existing code style
   - Add tests for new functionality
   - Update documentation as needed

4. **Test Your Changes**
   ```bash
   mvn test
   mvn clean package
   ```

5. **Commit Your Changes**
   ```bash
   git commit -m "Add feature: description of your changes"
   ```
   
   Follow conventional commit messages:
   - `feat:` for new features
   - `fix:` for bug fixes
   - `docs:` for documentation changes
   - `test:` for test additions/changes
   - `refactor:` for code refactoring
   - `style:` for formatting changes
   - `chore:` for maintenance tasks

6. **Push to Your Fork**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Open a Pull Request**
   - Provide a clear title and description
   - Reference any related issues
   - Include screenshots for UI changes
   - List any breaking changes

## Development Guidelines

### Code Style

- **Java**: Follow standard Java conventions
  - Use meaningful variable names
  - Keep methods small and focused
  - Add JavaDoc for public APIs
  - Proper exception handling

- **SQL**: 
  - Use uppercase for keywords
  - Proper indentation
  - Descriptive table/column names

- **JavaScript**:
  - Use consistent indentation
  - Add comments for complex logic
  - Follow existing jQuery patterns

### Testing

- Write unit tests for new functionality
- Maintain or improve code coverage
- Test edge cases and error conditions
- Use meaningful test names

```java
@Test
void testSearchWithEmptyQuery() {
    // Given
    String emptyQuery = "";
    
    // When
    SearchResponse response = searchService.search(emptyQuery, null, 0, 20);
    
    // Then
    assertFalse(response.isResult());
    assertEquals("Задан пустой поисковый запрос", response.getError());
}
```

### Documentation

- Update README.md for new features
- Add inline comments for complex logic
- Update API documentation if endpoints change
- Include usage examples

### Commit Messages

Good commit messages help understand the project history:

```
feat: add pagination support to search results

- Implement offset and limit parameters
- Add pagination controls to UI
- Update tests to cover pagination
- Update documentation

Closes #123
```

## Project Structure

```
searchengine/
├── src/
│   ├── main/
│   │   ├── java/searchengine/
│   │   │   ├── controllers/    # REST endpoints
│   │   │   ├── services/       # Business logic
│   │   │   ├── model/          # JPA entities
│   │   │   ├── repository/     # Data access
│   │   │   └── dto/            # DTOs
│   │   └── resources/
│   │       ├── static/         # Frontend assets
│   │       ├── templates/      # HTML templates
│   │       └── application.yaml
│   └── test/                   # Test files
├── library/                    # Lucene libraries
├── pom.xml                     # Maven configuration
└── README.md
```

## Review Process

1. All submissions require review
2. Maintainers will review your PR
3. Address any requested changes
4. Once approved, changes will be merged

## Questions?

Feel free to open an issue for questions or join discussions in existing issues.

## Recognition

Contributors will be acknowledged in the project. Thank you for your contributions!

---

Remember: The best contribution is one that helps others. Whether it's code, documentation, or bug reports, every contribution matters!
