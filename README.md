# DualQuickIME - 雙拼速成 (Dual Chinese/English Quick Input)

A dual-mode Chinese/English Input Method Editor (IME) for Android that supports 速成 (Quick/Simplified Cangjie) input **without switching keyboards**.

## Features

- **Dual-mode input**: Type Chinese (速成) and English seamlessly in one keyboard
- **Tap-to-select**: Tap candidates directly to select Chinese characters
- **Space pagination**: Press Space to navigate through candidate pages
- **OpenVanilla compatible**: Uses the same character ordering as OpenVanilla on macOS
- **QWERTY layout**: Each key shows both the English letter and Chinese radical

## How It Works

### Key Concept

The keyboard displays English letters (A-Z), but each key also corresponds to a Chinese radical. **No mode switching required** - the keyboard intelligently commits Chinese or English based on your actions.

| Key | Radical | Key | Radical | Key | Radical |
|-----|---------|-----|---------|-----|---------|
| A | 日 | J | 十 | S | 尸 |
| B | 月 | K | 大 | T | 廿 |
| C | 金 | L | 中 | U | 山 |
| D | 木 | M | 一 | V | 女 |
| E | 水 | N | 弓 | W | 田 |
| F | 火 | O | 人 | X | 難 |
| G | 土 | P | 心 | Y | 卜 |
| H | 竹 | Q | 手 | Z | 重 |
| I | 戈 | R | 口 |   |   |

### Input Flow

1. **Type 1-2 letters** → Candidates appear (e.g., `AB` → 明, 晴, 閒...)
2. **TAP a candidate** → Chinese character is committed
3. **Press SPACE** → Navigate to next page of candidates
4. **Type 3rd letter without selecting** → First 2 letters commit as English
5. **Press ENTER** → Composition commits as English

### Examples

| Input | Action | Result |
|-------|--------|--------|
| `A` `B` → tap 明 | Select Chinese | 明 |
| `H` `E` `L` | 3rd key typed | "he" + new composition |
| `A` `B` → SPACE → SPACE → tap | Navigate pages | Selected char |
| `H` `I` → tap 我 | Select Chinese | 我 |

### Mixed Input Example

To type "我love你":
1. `H` `I` → tap 我 → **我**
2. `L` `O` → type `V` (3rd key) → commits "lo"
3. `V` `E` → press ENTER → commits "ve"
4. `S` `P` → tap 你 → **你**

Result: **我love你**

## Building

### Prerequisites

- Android Studio Arctic Fox or newer
- JDK 17 or newer
- Android SDK with API 34

### Build Steps

```bash
# Clone the repository
git clone https://github.com/user/DualQuickIME.git
cd DualQuickIME

# Build debug APK
./gradlew assembleDebug

# The APK will be at: app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB

```bash
# Install the APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Enable the keyboard
adb shell ime enable com.example.dualquick/.DualQuickInputMethodService

# Set as default (optional)
adb shell ime set com.example.dualquick/.DualQuickInputMethodService
```

### Enable Manually

1. Go to **Settings → System → Languages & Input**
2. Tap **Virtual Keyboard** or **On-screen keyboard**
3. Tap **Manage keyboards**
4. Enable **DualQuick 中英速成**
5. Accept the security warning
6. Switch keyboard: Long-press the globe/keyboard icon in any text field

## Project Structure

```
DualQuickIME/
├── .github/workflows/build.yml    # CI/CD pipeline
├── app/src/main/
│   ├── java/com/example/dualquick/
│   │   ├── DualQuickInputMethodService.kt  # Main IME service
│   │   ├── data/
│   │   │   ├── CinParser.kt                # .cin file parser
│   │   │   ├── CompositionState.kt         # State with pagination
│   │   │   ├── SimplexEntry.kt             # Data entry class
│   │   │   └── SimplexTable.kt             # Lookup table
│   │   ├── ui/
│   │   │   ├── CandidateView.kt            # Tap-to-select candidates
│   │   │   └── KeyboardView.kt             # QWERTY + radicals
│   │   └── util/
│   │       └── KeyMapping.kt               # Key-to-radical mapping
│   ├── assets/
│   │   └── simplex.cin                     # OpenVanilla data (13K+ chars)
│   └── res/
│       ├── xml/method.xml                  # IME configuration
│       ├── values/                         # Strings, colors, themes
│       └── drawable/                       # Key backgrounds
└── build.gradle.kts
```

## Data Source

Character data is from [OpenVanilla](https://github.com/openvanilla/openvanilla), specifically the `simplex.cin` file which contains ~13,000 character mappings in Traditional Chinese frequency order.

## Key Behaviors

| Key | During Composition | IDLE State |
|-----|-------------------|------------|
| A-Z | Add to composition / commit English on 3rd key | Start composition |
| Space | **Next page** of candidates | Insert space |
| Enter | Commit as English | Send Enter |
| Backspace | Delete last character | Delete in text field |
| 0-9 | Commit composition as English, then number | Insert number |

## CI/CD

This project uses GitHub Actions for continuous integration. On every push:
- Builds debug and release APKs
- Runs unit tests
- Uploads APKs as artifacts

## License

MIT License

## Credits

- Character data: [OpenVanilla Project](https://github.com/openvanilla/openvanilla)
- 速成/Quick input method: Based on Cangjie by Chu Bong-Foo (朱邦復)
