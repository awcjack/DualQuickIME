# Offline candidate pronunciation data

Source: Unicode Consortium **Unihan 17.0.0** (Unicode Character Database).

- Archive: https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip
- SHA-256: `f7a48b2b545acfaa77b2d607ae28747404ce02baefee16396c5d2d7a8ef34b5e`
- Property definitions: https://www.unicode.org/reports/tr38/tr38-39.html
- License: `UNICODE-LICENSE.txt` in this directory (Unicode License v3).

`jyutping.tsv` contains 29,936 `kCantonese` readings, including tone numbers.
`pinyin.tsv` contains 44,348 Mandarin readings, including tone marks.
Each row is a hexadecimal Unicode code point, a tab, and one reading.

For Mandarin, prefer the reading with the largest usage count in `kHanyuPinlu`
(3,799 characters, from the Modern Standard Beijing Chinese Frequency Dictionary).
For example, 得 selects `de` (5,096 occurrences) over `dé` (1,496) and `děi` (637).
When no counts exist, use `kMandarin`. For Cantonese, use `kCantonese`. Unicode
defines the latter two properties as the **most customary** readings. These
curated defaults provide one sourced reading when measured counts are absent.
These defaults do not claim measured frequency in every variety of Chinese.

`kCantonese` has exactly one value per character in this pinned release. For
`kMandarin`, when two values exist, Unicode defines the first as the preferred
mainland Chinese reading and the second as the Taiwan preference. We use the
first as the fallback for the Hanyu pinyin option. English and candidates
containing unknown characters have no annotation. No pronunciations are inferred
from similar characters or from simplified/traditional variants.

## Suggested words and continuations

Word data is extracted from these pinned Rime dictionaries, limited to words
and continuations in the bundled OpenVanilla associated-phrases table:

- **Rime Cantonese**, maintained by CanCLID and contributors:
  https://github.com/rime/rime-cantonese
  Commit `259f0e48bba840c3a2e0d117539e96937f3d89bc`, file
  `jyut6ping3.words.dict.yaml`, SHA-256
  `54d174ad2bb997e4a678b7b076b84e4dc5914481b32467cdea3b43e88b7d5474`.
  Derived `jyutping-words.tsv`: 20,164 entries, CC BY 4.0;
  see `RIME-CANTONESE-LICENSE.txt`.
- **Terra Pinyin**, by 佛振 (Chen Sst) and Rime contributors, referencing
  CC-CEDICT published by MDBG: https://github.com/rime/rime-terra-pinyin
  Commit `723e51bc266cf9464530c1ddedb856aa18e3da34`, file
  `terra_pinyin.dict.yaml`, SHA-256
  `2f881a239e09a61e5993e79257655f98f81c3789dc8aeea5f33477f710345b27`.
  Derived `pinyin-words.tsv`: 6,610 entries, CC BY-SA 3.0;
  see `TERRA-PINYIN-LICENSE.txt`. Numeric tones are converted to tone marks.

Use an exact word reading when the source provides one unambiguous reading or
a uniquely highest-weight reading. Unweighted alternatives are not presumed
frequency ordered. Otherwise, join the sourced customary character readings.
For a suggestion that continues the last committed character, try that complete
word first and display only the suggested suffix's syllables: 銀 → 行 displays
`hong4` / `háng`, using 銀行. If a candidate contains any unknown character, omit
its annotation rather than showing an incomplete reading.

Regenerate from the repository root with:

```sh
curl -fL https://www.unicode.org/Public/17.0.0/ucd/Unihan.zip -o /tmp/Unihan.zip
python3 scripts/build_pronunciations.py /tmp/Unihan.zip
curl -fL https://raw.githubusercontent.com/rime/rime-cantonese/259f0e48bba840c3a2e0d117539e96937f3d89bc/jyut6ping3.words.dict.yaml -o /tmp/cantonese-words.yaml
curl -fL https://raw.githubusercontent.com/rime/rime-terra-pinyin/723e51bc266cf9464530c1ddedb856aa18e3da34/terra_pinyin.dict.yaml -o /tmp/terra-pinyin.yaml
python3 scripts/build_pronunciation_words.py /tmp/cantonese-words.yaml /tmp/terra-pinyin.yaml
```

The script verifies the archive checksum and generates deterministic output.
All lookups run offline in both full and lite builds.
