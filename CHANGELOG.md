# Changelog

All notable changes to DualQuickIME will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.8.0] - 2026-04-18
### Added
- **Simplified ⇄ Traditional Chinese Conversion**: New 簡⇄繁 key on the symbol keyboard converts the selected text using OpenCC
  - Auto-detects direction: Traditional input becomes Simplified, Simplified input becomes Traditional
  - Single tap; no long-press needed
  - Settings toggle to hide the key (full flavor only — lite ships without OpenCC)
- **Symbol-Mode Util Bar**: Emoji, clipboard, 簡⇄繁, and voice buttons moved into a dedicated bar above the number row
  - Frees up the symbol bottom row and keeps utility actions reachable while typing numbers/symbols
- **Configurable Candidate Spacing**: New seekbar in Settings → Keyboard adjusts candidate pill padding (2–14dp)
  - Smaller padding fits more candidates per row

### Fixed
- Candidate bar overflow: pills no longer get clipped past the right edge when English-character candidates or composition text change row width
- Candidate page indicator now reflects the corrected total after overflow rolls candidates to the next page
- Soft keyboard now shows reliably even when the framework reports a hardware keyboard attached
- KeyboardView recovers automatically (resets to letter mode and retries) if `loadTheme` or `buildKeyboard` throws, with the failure logged for diagnosis

## [1.7.1] - 2026-04-09
### Changed
- **Gboard-Style Symbol Keyboard**: Rearranged number/symbol keyboard to match Gboard layout
  - Page 1: `@ # $ % & - + ( )` and `* " ' : ; ! ?`
  - Page 2: `~ \` | • √ π ÷ × ¶ ∆` and `£ ¢ € ¥ ^ ° = { }`
- **Half-Width Default in Symbol Keyboard**: Symbol keyboard now outputs half-width characters by default
  - Long-press for full-width variants (reverse of main keyboard behavior)
  - Main keyboard retains full-width `，` `。` default with half-width on long-press
- **Comma and Period on Symbol Bottom Row**: Added `,` and `.` keys flanking the spacebar
  - Matches Gboard bottom row layout while preserving emoji and clipboard buttons

### Fixed
- Pending English text now committed when switching to number keyboard

## [1.7.0] - 2026-04-07
### Added
- **Complete Unicode 16.0 Emoji Support**: Expanded emoji keyboard from ~1,247 to 3,781 emojis
  - All skin tone variants (🏻🏼🏽🏾🏿) for supported emojis
  - All gender variants (♂️/♀️) for people activities
  - All ZWJ sequences (professions, families, couples)
  - Directional variants (🚶‍➡️, 🏃‍➡️, etc.)
  - All 270 flags including UK subdivisions (🏴󠁧󠁢󠁥󠁮󠁧󠁿, 🏴󠁧󠁢󠁳󠁣󠁴󠁿, 🏴󠁧󠁢󠁷󠁬󠁳󠁿)
  - Unicode 16.0 additions: 🫩 🫆 🪾 🫜 🪉 🪏 🫟 and more

## [1.6.5] - 2026-04-07
### Added
- **Quick Number Row**: Number keys (1-0) displayed in candidate bar when idle
  - Allows quick number input without switching to symbol keyboard
  - Numbers fill the entire candidate bar width with equal spacing
  - Automatically hides when typing to show candidates
  - Hidden in symbol mode to avoid duplication with number keyboard

### Fixed
- Candidate bar properly restored when returning from symbol/emoji/clipboard/grid views
- All candidates now display correctly (not just the first one) after switching views
- Fixed post callback on detached views causing candidate refresh failures

## [1.6.4] - 2026-04-07
### Security
- **Encrypted Clipboard History**: Clipboard data now encrypted at rest using AES256-GCM
  - Uses EncryptedSharedPreferences with automatic migration from old storage
  - Password field detection: skips capturing content from password inputs
  - Configurable TTL (default 24h) auto-expires non-pinned clipboard items
- **Disabled Android Backup**: Prevents clipboard history exposure via backup mechanisms
- **HTTPS-Only Network**: Added network security config enforcing HTTPS connections
- **Model Download Integrity**: SHA-256 checksum verification for voice model downloads
- **Debug Log Protection**: Sensitive speech recognition content no longer logged in release builds

### Fixed
- R8 minification failure in lite release build (added ProGuard rules for security-crypto)

## [1.6.3] - 2026-04-07
### Added
- **Emoji Skin Tone Selector**: Long-press emojis that support skin tones to choose from 6 variants
  - Selected skin tone becomes the default for all future emoji input
  - Preference persists across sessions
- **Grapheme-Aware Backspace**: Delete entire emoji (including skin tone modifiers) in one press
  - Uses ICU BreakIterator to properly handle multi-codepoint emojis
  - Works with ZWJ sequences, flags, and other complex emoji

### Fixed
- Emoji keyboard no longer scrolls to top after selecting a skin tone

## [1.6.2] - 2026-04-06
### Changed
- **Dynamic Candidate Pagination**: Candidates now flow to next page based on available width
  - Long associated phrases that don't fit are automatically moved to next page
  - At least one candidate always displays even if too wide
  - Improves usability for multi-character phrases
- Candidate bar height is now dynamic (expands for multi-line text)
- Candidate pills support up to 2 lines of text wrapping

### Fixed
- **Associated Phrase Display**: Multi-character phrases now display correctly
  - Removed horizontal scroll approach in favor of expandable boxes
  - Text wrapping enabled for long phrases
- **Candidate Grid View Size**: Grid view now stays within keyboard bounds
  - Fixed rows have 44dp height instead of expanding
  - Prevents grid from taking over entire screen

### Technical
- CompositionState tracks `displayOffset` and `lastDisplayedCount` for dynamic pagination
- KeyboardView.setCandidates() measures text width and returns count displayed
- Associated phrases mode uses offset-based pagination matching composition behavior

## [1.6.0] - 2026-04-05

### Added
- **U2pp-Conformer-Yue Voice Model**: State-of-the-art Cantonese ASR model
  - 5.05% MER (Mixed Error Rate) - best accuracy among small models
  - ~260 MB download size (int8 quantized)
  - Pre-converted model from sherpa-onnx, ready to use
  - Based on WenetSpeech-Yue 21,800+ hours training data

## [1.5.7] - 2026-04-05

### Added
- **Whisper Cantonese Voice Model**: Fine-tuned Whisper model optimized for Cantonese speech recognition
  - 7.93% CER (Character Error Rate) - best accuracy for Cantonese-only input
  - ~395 MB download size (int8 quantized)
  - Select in Settings > Voice Input > Voice Model
- **Voice Model Selection**: Choose between SenseVoice (multilingual) and Whisper Cantonese (optimized)
  - SenseVoice (~228 MB): Auto-detects Cantonese, Mandarin, English, Japanese, Korean
  - Whisper Cantonese (~395 MB): Best accuracy for Cantonese-focused input
- **Dynamic Model Updates**: Whisper Cantonese model auto-discovers latest release from GitHub
  - No app update needed when new model versions are published
  - 24-hour cache to respect GitHub API rate limits
  - Automatic fallback to bundled URL if API unavailable

### Changed
- Voice input now properly respects model type preference from settings
- Improved text processing: lowercase English output, CJK-aware OpenCC conversion
- Whisper special tokens (e.g., `<|transcribe|>`) are now stripped from output

### Fixed
- **Voice Recognition 4x Duplication Bug**: Fixed issue where speaking "hello" produced "hellohellohellohello"
  - VAD state is now properly reset between recording sessions
- Voice input button now correctly uses the selected model type
- OpenCC conversion no longer affects English text in mixed-language output
- Whisper Cantonese model conversion with proper KV-cache architecture and performance optimizations

### Technical
- Added VoiceModelType enum for extensible model management
- Custom HuggingFace to sherpa-onnx ONNX conversion with:
  - Pre-computed static causal mask for performance
  - Concatenation-based KV-cache updates (sherpa-onnx pattern)
  - Dynamic positional embedding slicing
- CI workflow for automated model conversion (convert-whisper-model.yml)
- Model version tracking to force re-download when format changes

## [1.4.1] - 2026-04-03

### Changed
- **R8 Minification**: Enabled R8 code minification and resource shrinking for release builds to reduce APK size
  - Added ProGuard keep rules for voice package, Sherpa-ONNX JNI, OpenCC, and enums

### Fixed
- Settings page now shows the correct IME version dynamically using BuildConfig.VERSION_NAME
- Recent candidate usage now recorded correctly before composition state changes
  - Fixed lookup code being cleared before recording when `clearComposition()` ran before `commitChinese()`
  - Fixed candidate selection with remaining buffer keys bypassing usage recording entirely

## [1.4.0] - 2026-04-03

### Added
- **Full-width Default Punctuation**: Main keyboard comma and period now output full-width `，` and `。` by default
  - Half-width `,` and `.` remain accessible on the symbol keyboard
- **View-All Candidates Grid**: Tap page indicator to open full-keyboard-space candidate grid
  - 7×5 grid layout with internal pagination and ◀/▶ navigation
  - ABC button to return to normal keyboard
  - Works with both composition candidates and associated phrases
- **Continuous Backspace Deletion**: Hold backspace to repeatedly delete characters
  - 400ms initial delay, 50ms repeat interval
  - Consistent behavior across letter, emoji, and clipboard keyboards
- **Recent Candidates Prioritization**: Frequently selected characters appear first
  - Toggle in Settings to enable/disable
  - Per-code usage tracking with LRU eviction (20 chars/code, 500 codes max)
  - Clear history option in Settings
- **Multi-Character Composition Buffer**: Removed 2-character auto-commit limit
  - Type multiple keys continuously; first 2 are used for character lookup
  - After selecting a candidate, remaining keys stay in the buffer for the next lookup
- **Full-width Punctuation on Symbol Keyboard**: Full-width `！` and `？` added
  - Placed in the second row of symbol page 1 for optimal layout

### Changed
- Default punctuation switched from half-width to full-width for Chinese input conventions

### Fixed
- Candidate grid view not updating after selecting candidate with remaining composition keys
- Candidate grid view last page expanding to fill entire screen instead of wrapping content

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
