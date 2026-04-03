# Changelog

All notable changes to DualQuickIME will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.4.0] - 2026-04-03

### Added
- **Full-width Punctuation**: Full-width exclamation mark (！) and question mark (？) on symbol keyboard
  - Available on the first special character page for quick access
  - Placed in the second row alongside other common punctuation for optimal layout

## [1.3.0] - 2026-04-03

### Added
- **Associated Phrases**: Word suggestions based on last committed character
  - After committing a Chinese character, shows related phrases/words
  - Based on OpenVanilla's associated-phrases.cin database (~50,000 entries)
  - Press space to navigate pages, tap to select and continue chain
  - Automatically chains: selecting a phrase shows suggestions for its last character

## [1.2.0] - 2026-04-03

### Added
- **Offline Voice Input with SenseVoice**: High-accuracy speech recognition for Cantonese, Mandarin, and English
  - Uses SenseVoice model fine-tuned on 21.8k hours of Cantonese data
  - Silero VAD for voice activity detection and endpoint detection
  - Works completely offline after model download (~227 MB)
  - Two-button UI: Reset/Cancel and Commit for manual control
  - No auto-commit - text is only committed when user presses Commit button
- **OpenCC Integration**: Comprehensive Simplified to Traditional Chinese conversion
  - Phrase-aware conversion with 2,500+ character mappings
  - Uses Hong Kong Traditional variant (s2hk) for best Cantonese support
- **Punctuation Conversion**: Speak punctuation names to insert symbols
  - Supports Cantonese (逗號, 句號, etc.), Mandarin (逗号, 句号, etc.), and English (comma, period, etc.)
- **Build Flavors**: Two APK variants
  - Full: With voice input support (~30 MB per ABI)
  - Lite: Without voice input, no network permissions (~3 MB)
- **ABI Splits**: Separate APKs for arm64-v8a, armeabi-v7a, and universal

### Changed
- Voice input uses official k2-fsa sherpa-onnx AAR with full VAD support
- Voice model switched from streaming Paraformer to non-streaming SenseVoice for better accuracy
- CI now produces separate artifacts by flavor AND architecture (12 total APKs)

### Fixed
- Reset button now properly clears accumulated voice text
- Voice input API compatibility with sherpa-onnx library

### Technical
- Official sherpa-onnx AAR v1.12.34 with VAD support
- OpenccJava v1.2.0 for S2T conversion
- Model files downloaded during CI build to avoid large git commits

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
