# CI/CD Setup Guide

This document explains the CI/CD pipeline setup for the BurnerPhone project.

## Overview

The project uses GitHub Actions for continuous integration and deployment. Two main workflows are configured:

1. **build.yml** - Main CI pipeline for builds and tests
2. **release.yml** - Automated release creation

## Workflows

### Build Workflow (build.yml)

Triggers on:
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop` branches

**Jobs:**

#### Build and Test
- Sets up JDK 17
- Caches Gradle dependencies
- Builds the project with `./gradlew build`
- Runs unit tests with `./gradlew test`
- Runs lint checks with `./gradlew lint`
- Uploads test results and reports as artifacts
- Builds debug and release APKs
- Uploads APKs as downloadable artifacts

#### Code Quality Checks
- Runs detekt for static code analysis (if configured)
- Runs ktlintCheck for code formatting validation (if configured)
- Uploads code quality reports

### Release Workflow (release.yml)

Triggers on:
- Push of version tags matching `v*` pattern (e.g., `v1.0.0`)

**Jobs:**

#### Create Release
- Builds release APK
- Creates GitHub release with the APK attached
- Optionally signs APK if keystore is configured

## Prerequisites

### Gradle Wrapper

The project needs the Gradle wrapper files to run CI builds. If not present, generate them:

```bash
# In Android Studio: File → Sync Project with Gradle Files
# Or manually generate:
gradle wrapper --gradle-version 8.2
```

This creates:
- `gradlew` (Unix/Mac script)
- `gradlew.bat` (Windows script)
- `gradle/wrapper/` directory

**Important:** Commit these files to the repository!

### Optional: Code Quality Tools

To enable detekt and ktlint checks, add to `app/build.gradle.kts`:

```kotlin
plugins {
    // ... existing plugins
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
}

dependencies {
    // ... existing dependencies
    detekt("io.gitlab.arturbosch.detekt:detekt-cli:1.23.4")
}

detekt {
    config = files("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}
```

For ktlint, add:

```kotlin
plugins {
    // ... existing plugins
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}
```

### Optional: App Signing for Releases

To sign release APKs, configure GitHub secrets:

1. Go to repository Settings → Secrets → Actions
2. Add the following secrets:
   - `KEYSTORE_FILE`: Base64-encoded keystore file
   - `KEYSTORE_PASSWORD`: Keystore password
   - `KEY_ALIAS`: Key alias
   - `KEY_PASSWORD`: Key password

Update the release workflow to use these secrets for signing.

## Using the CI Pipeline

### For Development

1. **Create a branch** for your feature
2. **Push commits** - CI runs on every push
3. **Create PR** - CI runs again, shows results in PR
4. **Review build status** - Green checkmark means all tests passed
5. **Download artifacts** - APKs available in Actions tab

### For Releases

1. **Ensure all tests pass** on main branch
2. **Create a version tag**:
   ```bash
   git tag -a v1.0.0 -m "Release version 1.0.0"
   git push origin v1.0.0
   ```
3. **Release workflow triggers** automatically
4. **GitHub release created** with APK attached
5. **Download release APK** from Releases page

## Build Artifacts

The CI pipeline creates several artifacts:

- **app-debug.apk** - Debug build for testing
- **app-release-unsigned.apk** - Release build (or signed if configured)
- **test-results/** - JUnit test results
- **lint-results-*.html** - Lint check reports
- **detekt-results/** - Code quality reports (if enabled)

Artifacts are retained for 30 days (90 days for releases).

## Status Badges

The README includes status badges that show:
- Build status (passing/failing)
- Release workflow status
- License information
- Minimum Android API level

These update automatically based on workflow runs.

## Troubleshooting

### Build Fails: "gradlew not found"

**Solution:** Add Gradle wrapper files to the repository:
```bash
gradle wrapper --gradle-version 8.2
git add gradlew gradlew.bat gradle/
git commit -m "Add Gradle wrapper"
```

### Build Fails: "Could not resolve dependencies"

**Solution:** Ensure `gradle.properties` is configured correctly and all dependencies in `build.gradle.kts` are available.

### Lint Fails: "detekt not configured"

**Solution:** Either configure detekt (see above) or remove detekt step from workflow.

### Tests Fail in CI but Pass Locally

**Solution:** Check for:
- Time-dependent tests (use mocked clocks)
- File path issues (use platform-independent paths)
- Database initialization (ensure proper setup/teardown)

## Performance Optimization

To speed up CI builds:

1. **Gradle cache** is already configured
2. **Parallel builds**: Add to `gradle.properties`:
   ```properties
   org.gradle.parallel=true
   org.gradle.caching=true
   ```
3. **Incremental builds**: Already enabled by default

## Security

- **Secrets**: Never commit sensitive data like keystores or passwords
- **Dependencies**: Dependabot automatically checks for security updates
- **Code scanning**: GitHub Code Scanning can be enabled for additional security

## Future Enhancements

Planned CI/CD improvements:
- [ ] Code coverage reporting with Codecov/Coveralls
- [ ] Automated dependency updates
- [ ] Performance benchmarking
- [ ] Screenshot testing for UI
- [ ] Automated Play Store deployment (requires app signing)
