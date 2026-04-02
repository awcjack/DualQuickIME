# Changelog

All notable changes to DualQuickIME will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2024-04-02

### Added
- Initial release of DualQuickIME
- Dual-mode Chinese input using Quick (速成) method
- QWERTY keyboard layout with radical hints
- Full emoji keyboard with categories (Smileys, Animals, Food, Activities, Travel, Objects, Symbols, Flags)
- Special character keyboard with 5 pages:
  - Page 1: Numbers and common punctuation
  - Page 2: Brackets and math symbols
  - Page 3: Currency and unit symbols
  - Page 4: Miscellaneous symbols
  - Page 5: Arrows and shapes
- Multi-language support:
  - English
  - Traditional Chinese (Taiwan)
  - Traditional Chinese (Hong Kong)
  - Simplified Chinese
- Theme support:
  - System default (follows device theme)
  - Light mode
  - Dark mode
- Settings activity with:
  - Theme selection
  - Composition display toggle
  - Candidates per page configuration
  - Keyboard preview
- Candidate bar with pagination
- English word suggestions
- Space key for pagination through candidates
- OpenVanilla simplex.cin compatibility

### Technical
- Package: `com.awcjack.dualquickime`
- Min SDK: 24 (Android 7.0)
- Target SDK: 34 (Android 14)
- Material Design 3 inspired UI
- GitHub Actions CI/CD pipeline
