# Contributing to Simple Stocks Widget

Thank you for your interest in contributing to Simple Stocks Widget! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing](#testing)
- [Issue Guidelines](#issue-guidelines)

## Code of Conduct

This project follows a simple code of conduct:
- Be respectful and inclusive
- Focus on constructive feedback
- Help maintain a welcoming environment for all contributors
- Keep discussions focused on the technical aspects of the project

## Getting Started

### Prerequisites

- **Android Studio** (latest stable version)
- **JDK 11** or higher
- **Android SDK** with API 26+ (Android 8.0)
- **Git** for version control
- **Finnhub API key** (required for real data - free at [finnhub.io](https://finnhub.io))

### Development Setup

1. **Fork and Clone**
   ```bash
   git clone https://github.com/yourusername/simple-stocks-widget.git
   cd simple-stocks-widget
   ```

2. **Open in Android Studio**
   - File → Open → Select project folder
   - Wait for Gradle sync to complete

3. **Get Finnhub API Key**
   - Visit [Finnhub.io](https://finnhub.io) and create a free account
   - Copy your API key from the dashboard
   - Run the app and enter your API key in settings
   - Test with a few stock symbols (AAPL, SPY, etc.)

4. **Test on Device**
   - Real device recommended (emulator has network issues)
   - Enable USB debugging or use wireless debugging

## How to Contribute

### Types of Contributions Welcome

- 🐛 **Bug fixes**
- ✨ **New features** (discuss in issues first)
- 📚 **Documentation improvements**
- 🎨 **UI/UX enhancements**
- ⚡ **Performance optimizations**
- 🧪 **Test coverage improvements**
- 🌐 **Localization/translations**

### Before You Start

1. **Check existing issues** - Someone might already be working on it
2. **Create an issue** for new features to discuss approach
3. **Comment on issues** you'd like to work on
4. **Keep scope small** - Smaller PRs are easier to review

## Pull Request Process

### 1. Create Feature Branch
```bash
git checkout -b feature/your-feature-name
# or
git checkout -b fix/issue-number-description
```

### 2. Make Changes
- Follow existing code style and patterns
- Test thoroughly on real device
- Update documentation if needed
- Keep commits focused and atomic

### 3. Test Your Changes
- **Widget functionality**: Add/remove widgets, test configuration
- **API integration**: Test with real API key and demo mode
- **Battery optimization**: Verify market hours logic
- **Theme support**: Test light/dark modes
- **Edge cases**: Empty data, network errors, etc.

### 4. Commit Guidelines
```bash
# Use conventional commit format
git commit -m "feat: add support for crypto symbols"
git commit -m "fix: resolve widget update crash on Android 12"
git commit -m "docs: update installation instructions"
```

**Commit Types:**
- `feat:` New features
- `fix:` Bug fixes
- `docs:` Documentation
- `style:` Code style (no logic changes)
- `refactor:` Code restructuring
- `test:` Adding tests
- `perf:` Performance improvements

### 5. Push and Create PR
```bash
git push origin feature/your-feature-name
```

- Create pull request with clear title and description
- Link related issues with "Fixes #123" or "Closes #456"
- Add screenshots/recordings for UI changes
- Request review from maintainers

## Coding Standards

### Kotlin Style
- Follow [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- Use meaningful variable and function names
- Add comments for complex logic
- Prefer `val` over `var` when possible

### Architecture Patterns
- **MainActivity**: Jetpack Compose for settings UI
- **Widget Providers**: Handle widget lifecycle and updates
- **Services**: Background data fetching with coroutines
- **Data Classes**: Immutable data structures
- **Caching**: Use StockDataCache for efficient data management

### Code Examples

**Good Widget Update:**
```kotlin
fun updateWidget(context: Context, appWidgetId: Int) {
    val stockData = StockDataCache.getStockData(symbol)
    val views = RemoteViews(context.packageName, R.layout.widget_2x1_layout)
    
    views.setTextViewText(R.id.price_text, "$${String.format("%.2f", stockData.price)}")
    
    // Set colors based on change
    val color = if (stockData.change >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
    views.setTextColor(R.id.change_text, color)
    
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
```

**Good API Call:**
```kotlin
private suspend fun fetchStockData(symbol: String, apiKey: String): StockData = withContext(Dispatchers.IO) {
    val connection = URL("https://finnhub.io/api/v1/quote?symbol=$symbol").openConnection() as HttpURLConnection
    connection.setRequestProperty("X-Finnhub-Token", apiKey)
    
    val response = connection.inputStream.bufferedReader().readText()
    val json = JSONObject(response)
    
    StockData(
        symbol = symbol,
        price = json.getDouble("c"),
        change = json.getDouble("d"),
        percentChange = json.getDouble("dp")
    )
}
```

## Testing

### Manual Testing Checklist

**Widget Functionality:**

- [ ] Add 2×1 widget to home screen
- [ ] Add 1×1 widget to home screen
- [ ] Configure different stock symbols
- [ ] Verify data updates during market hours
- [ ] Verify no updates outside market hours
- [ ] Test with and without API key

**App Interface:**

- [ ] Settings save properly
- [ ] API test works with valid key
- [ ] Demo mode works without key
- [ ] Light/dark theme switching
- [ ] Status bar appearance

**Edge Cases:**

- [ ] Invalid stock symbols
- [ ] Network connectivity issues
- [ ] API rate limiting
- [ ] Device restart (widget persistence)
- [ ] App updates (widget migration)

### Automated Testing
Currently the project relies on manual testing, but contributions for unit tests are welcome:
- **API integration tests**
- **Widget provider tests**
- **Data caching tests**
- **Market hours logic tests**

## Issue Guidelines

### Reporting Bugs

**Use the bug report template:**
```markdown
**Describe the bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. See error

**Expected behavior**
What you expected to happen.

**Screenshots/Logs**
Add screenshots or logcat output if applicable.

**Device Info:**
- Android version: [e.g. Android 12]
- Device: [e.g. Pixel 6]
- App version: [e.g. 1.0.0]

**API Key**
- Using API key: Yes/No
- Using demo mode: Yes/No
```

### Feature Requests

**Consider these questions:**
- Does this align with the app's "simple and clean" philosophy?
- Would this benefit most users?
- Is this feasible with current architecture?
- Are there alternative solutions?

**Use the feature request template:**
```markdown
**Feature Description**
Clear description of the proposed feature.

**Use Case**
Why would this be useful? What problem does it solve?

**Proposed Solution**
How do you envision this working?

**Alternatives Considered**
Any alternative approaches you've thought about?

**Additional Context**
Screenshots, mockups, or other relevant information.
```

## Release Process

### Versioning
- Follow [Semantic Versioning](https://semver.org/)
- `MAJOR.MINOR.PATCH` format
- Major: Breaking changes
- Minor: New features (backward compatible)
- Patch: Bug fixes

### Release Checklist
1. Update version in `build.gradle.kts`
2. Update `CHANGELOG.md` with release notes
3. Create release branch: `release/v1.x.x`
4. Test thoroughly on multiple devices
5. Create GitHub release with APK
6. Merge to main and tag

## Questions?

Feel free to:
- Open an issue for questions
- Start a discussion for broader topics
- Reach out to maintainers for guidance

Thank you for contributing to Simple Stocks Widget! 🚀