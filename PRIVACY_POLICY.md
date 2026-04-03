# Privacy Policy for DualQuickIME

**Last updated:** April 2026

## Overview

DualQuickIME is an open-source input method editor (IME) for Android that provides Chinese character input using a dual-key approach. This privacy policy explains how DualQuickIME handles your data.

## Data Collection

**DualQuickIME does NOT collect, store, or transmit any user data.**

Specifically:

- **No keystroke logging**: Your typed text is not recorded or stored
- **No personal information**: We do not collect names, emails, or any identifying information
- **No analytics**: We do not use any analytics or tracking services
- **No network access**: The app does not require or use internet connectivity
- **No cloud storage**: All data stays on your device

## Permissions

DualQuickIME only requires the minimum permissions necessary for its features:

- **Input Method Service**: Required to function as a keyboard (system permission)
- **Internet** (optional): Only used to download the voice recognition model (~228 MB). No other network activity occurs.
- **Record Audio** (optional): Only used for voice input feature. Audio is processed entirely on-device.

The app does NOT request:
- Location access
- Access to contacts or files
- Any other sensitive permissions

## Data Storage

The only data stored locally on your device:

- **User preferences**: Theme selection, display settings (stored in Android SharedPreferences)
- **Character mapping data**: The simplex.cin dictionary file bundled with the app
- **Clipboard history** (optional): If enabled, stores recently typed or pasted text for quick access

This data never leaves your device.

## Clipboard History Feature

DualQuickIME includes an optional clipboard history feature that allows you to access recently typed or pasted text:

- **Enabled by default**: Clipboard history captures text you copy from any app
- **Local storage only**: All clipboard data is stored locally on your device using SharedPreferences
- **No transmission**: Clipboard data is never sent to any server or third party
- **User control**: You can:
  - Enable or disable the feature at any time in Settings
  - Clear all clipboard history with one tap
  - Pin frequently used items for easy access
  - Delete individual items
- **Limited retention**: Maximum of 50 items are stored, with oldest items automatically removed

## Voice Input Feature

DualQuickIME includes an optional offline voice recognition feature:

- **Fully offline**: All speech recognition happens on your device using Sherpa-ONNX
- **Model download**: A one-time download of ~228 MB is required for the voice model
- **No audio transmission**: Your voice is never sent to any server
- **Languages**: Supports Cantonese, Mandarin Chinese, and English
- **User control**: You can:
  - Enable or disable voice input in Settings
  - Download or delete the voice model at any time
  - Grant or revoke audio recording permission

## Third-Party Services

DualQuickIME uses the following open-source components:

- **Sherpa-ONNX**: Offline speech recognition library (https://github.com/k2-fsa/sherpa-onnx)
  - Models are downloaded from HuggingFace (one-time download)
  - All processing is done locally on your device
  - No data is sent to any server

No third-party analytics, advertising, or tracking services are used.

## Open Source

DualQuickIME is open source. You can review the complete source code at:
https://github.com/awcjack/DualQuickIME

## Children's Privacy

DualQuickIME does not collect any personal information from anyone, including children under 13.

## Changes to This Policy

If we make changes to this privacy policy, we will update the "Last updated" date above.

## Contact

If you have questions about this privacy policy, please open an issue on our GitHub repository:
https://github.com/awcjack/DualQuickIME/issues

## Summary

**Your privacy is fully protected. DualQuickIME processes all input locally on your device. The only network access is an optional one-time download of the voice recognition model. No personal data, keystrokes, or voice recordings are ever transmitted.**
