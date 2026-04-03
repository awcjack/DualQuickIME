# Changelog

All notable changes to DualQuickIME will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2025-04-03

### Added
- **Clipboard History**: Gboard-style clipboard with pinning support
  - Access via 📋 button on symbol keyboard
  - Store up to 50 recent items, pin up to 10 for quick access
  - Captures text copied from any app via system clipboard
  - Two tabs: All and Pinned
  - Enable/disable and clear history in Settings
- **Caps Lock Mode**: Double-tap shift key within 300ms to enable
  - Visual indicator with inverted background colors
  - Stays uppercase until tapped again to disable
- **Extended Character Set**: simplex-ext.cin with 63,189 characters
  - Full Cantonese character support (嘢, 嚟, 喺, 唔, 咗, 嘅, 噉, 佢, 哋, 啲, 乜, 冇, 睇, 攞, 嬲)
  - CNS11643 and Unicode extended characters
  - Option in Settings to switch between Standard (13K) and Extended (63K)
- **About Section**: Links to GitHub repository in Settings

### Fixed
- Uppercase letters now display correctly in candidate preview
- Uppercase state preserved when committing English letters
- Clipboard duplicate entries when pasting from IME clipboard
- Kotlin scope resolution in GradientDrawable apply block

### Changed
- Extended character set is now the default
- Clipboard captures from system clipboard only (not IME committed text)

### Technical
- CI generates changelog from conventional commits
- Release workflow creates GitHub releases with signed APKs

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
