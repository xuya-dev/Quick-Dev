# Contributor Guide (English)

Thanks for your interest in Quick Dev! Contributions via Issues and Pull Requests are welcome.

简体中文 | [English](CONTRIBUTING.md)

*(The authoritative version of this guide is [CONTRIBUTING.md](CONTRIBUTING.md) (Chinese); this file is a reference
translation.)*

## Requirements

- JDK 17+ (build target 17, tested on 21)
- Maven 3.8+
- Git

## Getting Started

```bash
git clone https://github.com/xuya-dev/Quick-Dev.git
cd quick-dev
mvn clean verify      # build and run all tests
```

## Pull Requests

1. Fork and branch from `main` (`feat/xxx`, `fix/xxx`)
2. Ensure `mvn clean verify` passes; add tests for new features
3. Follow the commit format `<emoji> <Type>|<类型> <short description>`, e.g. `✨ Features|新功能 add TREE endpoint`
4. Update `CHANGELOG.md` (newest first)
5. Public API changes must update both `README.md` and `README.en.md`

## Code Style

- Java 17; no hard dependencies in core (new dependencies must be `optional` and aggregated by the starter)
- Javadoc in Chinese; clear examples for public APIs
- One PR does one thing

## License

By submitting, you agree your contribution is licensed under [Apache License 2.0](LICENSE).
