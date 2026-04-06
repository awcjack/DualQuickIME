package com.awcjack.dualquickime.data

/**
 * Comprehensive emoji data organized by categories.
 */
object EmojiData {

    // Skin tone modifiers (Fitzpatrick scale)
    val skinTones = listOf(
        "",      // Default yellow
        "\uD83C\uDFFB",  // Light skin tone
        "\uD83C\uDFFC",  // Medium-light skin tone
        "\uD83C\uDFFD",  // Medium skin tone
        "\uD83C\uDFFE",  // Medium-dark skin tone
        "\uD83C\uDFFF"   // Dark skin tone
    )

    // Emojis that support skin tone modifiers (base forms without modifiers)
    // These are primarily hand gestures, people, and body parts
    private val skinToneEmojiSet = setOf(
        // Hand gestures
        "👋", "🤚", "🖐", "✋", "🖖", "👌", "🤌", "🤏", "✌", "🤞",
        "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝", "👍",
        "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
        "🙏", "✍", "💅", "🤳", "💪",
        // Body parts
        "🦵", "🦶", "👂", "🦻", "👃",
        // People
        "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩",
        "🧓", "👴", "👵", "🙍", "🙎", "🙅", "🙆", "💁", "🙋", "🧏",
        "🙇", "🤦", "🤷", "👮", "🕵", "💂", "🥷", "👷", "🤴", "👸",
        "👳", "👲", "🧕", "🤵", "👰", "🤰", "🤱", "👼", "🎅", "🤶",
        "🦸", "🦹", "🧙", "🧚", "🧛", "🧜", "🧝", "🧞", "🧟",
        // Activities with people
        "🏋", "🤼", "🤸", "⛹", "🤺", "🤾", "🏌", "🏇", "🧘",
        "🏄", "🏊", "🤽", "🚣", "🧗", "🚴", "🚵",
        // Other
        "🛀", "🛌", "🧖", "🧗", "🧘", "🧙", "🧚", "🧛", "🧜", "🧝"
    )

    /**
     * Check if an emoji supports skin tone modifiers.
     * Handles both base emojis and emojis with variation selectors.
     */
    fun supportsSkinTone(emoji: String): Boolean {
        // Get the base emoji (first grapheme cluster without modifiers)
        val base = getBaseEmoji(emoji)
        return skinToneEmojiSet.contains(base)
    }

    /**
     * Get the base emoji without skin tone modifier.
     */
    fun getBaseEmoji(emoji: String): String {
        if (emoji.isEmpty()) return emoji

        // Remove skin tone modifiers and variation selectors
        val result = StringBuilder()
        var i = 0
        while (i < emoji.length) {
            val codePoint = emoji.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            // Skip skin tone modifiers (U+1F3FB to U+1F3FF)
            if (codePoint in 0x1F3FB..0x1F3FF) {
                i += charCount
                continue
            }
            // Skip variation selector-16 (U+FE0F)
            if (codePoint == 0xFE0F) {
                i += charCount
                continue
            }

            result.appendCodePoint(codePoint)
            i += charCount
        }
        return result.toString()
    }

    /**
     * Apply a skin tone modifier to an emoji.
     * @param emoji The base emoji
     * @param skinToneIndex 0 = default (no modifier), 1-5 = light to dark
     */
    fun applySkiTone(emoji: String, skinToneIndex: Int): String {
        if (skinToneIndex == 0 || !supportsSkinTone(emoji)) {
            return getBaseEmoji(emoji)
        }

        val base = getBaseEmoji(emoji)
        val modifier = skinTones.getOrElse(skinToneIndex) { "" }

        if (modifier.isEmpty() || base.isEmpty()) return base

        // Insert skin tone modifier after the first code point
        val firstCodePoint = base.codePointAt(0)
        val firstCharCount = Character.charCount(firstCodePoint)

        return base.substring(0, firstCharCount) + modifier + base.substring(firstCharCount)
    }

    /**
     * Get all skin tone variants for an emoji.
     * Returns list of 6 variants: default + 5 skin tones
     */
    fun getSkinToneVariants(emoji: String): List<String> {
        val base = getBaseEmoji(emoji)
        if (!supportsSkinTone(base)) {
            return listOf(emoji)
        }

        return skinTones.mapIndexed { index, _ ->
            applySkiTone(base, index)
        }
    }

    // Quick access lists for EmojiKeyboardView
    val smileys get() = categories[0].emojis
    val gestures get() = categories[1].emojis
    val animals get() = categories[2].emojis
    val food get() = categories[3].emojis
    val activities get() = categories[4].emojis
    val travel get() = categories[5].emojis
    val objects get() = categories[6].emojis
    val symbols get() = categories[7].emojis
    val flags get() = categories[8].emojis

    data class EmojiCategory(
        val name: String,
        val icon: String,
        val emojis: List<String>
    )

    val categories = listOf(
        EmojiCategory(
            name = "Smileys",
            icon = "😀",
            emojis = listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
                "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
                "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫",
                "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬",
                "😮‍💨", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
                "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳",
                "🥸", "😎", "🤓", "🧐", "😕", "😟", "🙁", "☹️", "😮", "😯",
                "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
                "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡",
                "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺",
                "👻", "👽", "👾", "🤖", "😺", "😸", "😹", "😻", "😼", "😽",
                "🙀", "😿", "😾"
            )
        ),
        EmojiCategory(
            name = "Gestures",
            icon = "👋",
            emojis = listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞",
                "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍",
                "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
                "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂",
                "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "🦴", "👀", "👁️", "👅",
                "👄", "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩",
                "🧓", "👴", "👵", "🙍", "🙎", "🙅", "🙆", "💁", "🙋", "🧏",
                "🙇", "🤦", "🤷", "👮", "🕵️", "💂", "🥷", "👷", "🤴", "👸"
            )
        ),
        EmojiCategory(
            name = "Animals",
            icon = "🐶",
            emojis = listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨",
                "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊",
                "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉",
                "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌",
                "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🕸️", "🦂",
                "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀",
                "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆",
                "🦓", "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒",
                "🦘", "🦬", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🦙",
                "🐐", "🦌", "🐕", "🐩", "🦮", "🐕‍🦺", "🐈", "🐈‍⬛", "🪶", "🐓",
                "🦃", "🦤", "🦚", "🦜", "🦢", "🦩", "🕊️", "🐇", "🦝", "🦨",
                "🦡", "🦫", "🦦", "🦥", "🐁", "🐀", "🐿️", "🦔"
            )
        ),
        EmojiCategory(
            name = "Food",
            icon = "🍔",
            emojis = listOf(
                "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
                "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
                "🥦", "🥬", "🥒", "🌶️", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅",
                "🥔", "🍠", "🥐", "🥯", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳",
                "🧈", "🥞", "🧇", "🥓", "🥩", "🍗", "🍖", "🦴", "🌭", "🍔",
                "🍟", "🍕", "🫓", "🥪", "🥙", "🧆", "🌮", "🌯", "🫔", "🥗",
                "🥘", "🫕", "🥫", "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟",
                "🦪", "🍤", "🍙", "🍚", "🍘", "🍥", "🥠", "🥮", "🍢", "🍡",
                "🍧", "🍨", "🍦", "🥧", "🧁", "🍰", "🎂", "🍮", "🍭", "🍬",
                "🍫", "🍿", "🍩", "🍪", "🌰", "🥜", "🍯", "🥛", "🍼", "🫖",
                "☕", "🍵", "🧃", "🥤", "🧋", "🍶", "🍺", "🍻", "🥂", "🍷",
                "🥃", "🍸", "🍹", "🧉", "🍾", "🧊"
            )
        ),
        EmojiCategory(
            name = "Activities",
            icon = "⚽",
            emojis = listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
                "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳",
                "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷",
                "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "⛹️",
                "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗",
                "🚴", "🚵", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🪘", "🎷",
                "🎺", "🪗", "🎸", "🪕", "🎻", "🎲", "♟️", "🎯", "🎳", "🎮",
                "🎰", "🧩", "🎨", "🧵", "🪡", "🧶", "🪢"
            )
        ),
        EmojiCategory(
            name = "Travel",
            icon = "🚗",
            emojis = listOf(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐",
                "🛻", "🚚", "🚛", "🚜", "🦯", "🦽", "🦼", "🛴", "🚲", "🛵",
                "🏍️", "🛺", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟",
                "🚃", "🚋", "🚞", "🚝", "🚄", "🚅", "🚈", "🚂", "🚆", "🚇",
                "🚊", "🚉", "✈️", "🛫", "🛬", "🛩️", "💺", "🛰️", "🚀", "🛸",
                "🚁", "🛶", "⛵", "🚤", "🛥️", "🛳️", "⛴️", "🚢", "⚓", "🪝",
                "⛽", "🚧", "🚦", "🚥", "🚏", "🗺️", "🗿", "🗽", "🗼", "🏰",
                "🏯", "🏟️", "🎡", "🎢", "🎠", "⛲", "⛱️", "🏖️", "🏝️", "🏜️",
                "🌋", "⛰️", "🏔️", "🗻", "🏕️", "⛺", "🛖", "🏠", "🏡", "🏘️",
                "🏚️", "🏗️", "🏭", "🏢", "🏬", "🏣", "🏤", "🏥", "🏦", "🏨",
                "🏪", "🏫", "🏩", "💒", "🏛️", "⛪", "🕌", "🕍", "🛕", "🕋"
            )
        ),
        EmojiCategory(
            name = "Objects",
            icon = "💡",
            emojis = listOf(
                "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️",
                "🗜️", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥",
                "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️",
                "🎛️", "🧭", "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋",
                "🔌", "💡", "🔦", "🕯️", "🪔", "🧯", "🛢️", "💸", "💵", "💴",
                "💶", "💷", "🪙", "💰", "💳", "💎", "⚖️", "🪜", "🧰", "🪛",
                "🔧", "🔨", "⚒️", "🛠️", "⛏️", "🪚", "🔩", "⚙️", "🪤", "🧱",
                "⛓️", "🧲", "🔫", "💣", "🧨", "🪓", "🔪", "🗡️", "⚔️", "🛡️",
                "🚬", "⚰️", "🪦", "⚱️", "🏺", "🔮", "📿", "🧿", "💈", "⚗️",
                "🔭", "🔬", "🕳️", "🩹", "🩺", "💊", "💉", "🩸", "🧬", "🦠",
                "🧫", "🧪", "🌡️", "🧹", "🪠", "🧺", "🧻", "🚽", "🚰", "🚿",
                "🛁", "🛀", "🧼", "🪥", "🪒", "🧽", "🪣", "🧴", "🛎️", "🔑",
                "🗝️", "🚪", "🪑", "🛋️", "🛏️", "🛌", "🧸", "🪆", "🖼️", "🪞",
                "🪟", "🛍️", "🛒", "🎁", "🎈", "🎏", "🎀", "🪄", "🪅", "🎊",
                "🎉", "🎎", "🏮", "🎐", "🧧", "✉️", "📩", "📨", "📧", "💌"
            )
        ),
        EmojiCategory(
            name = "Symbols",
            icon = "❤️",
            emojis = listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️",
                "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐",
                "⛎", "♈", "♉", "♊", "♋", "♌", "♍", "♎", "♏", "♐",
                "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳",
                "🈶", "🈚", "🈸", "🈺", "🈷️", "✴️", "🆚", "💮", "🉐", "㊙️",
                "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️",
                "🆘", "❌", "⭕", "🛑", "⛔", "📛", "🚫", "💯", "💢", "♨️",
                "🚷", "🚯", "🚳", "🚱", "🔞", "📵", "🚭", "❗", "❕", "❓",
                "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️",
                "🔰", "♻️", "✅", "🈯", "💹", "❇️", "✳️", "❎", "🌐", "💠",
                "Ⓜ️", "🌀", "💤", "🏧", "🚾", "♿", "🅿️", "🛗", "🈳", "🈂️",
                "🛂", "🛃", "🛄", "🛅", "🚹", "🚺", "🚼", "⚧️", "🚻", "🚮",
                "🎦", "📶", "🈁", "🔣", "ℹ️", "🔤", "🔡", "🔠", "🆖", "🆗",
                "🆙", "🆒", "🆕", "🆓", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣",
                "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟", "#️⃣", "*️⃣", "⏏️", "▶️", "⏸️",
                "⏯️", "⏹️", "⏺️", "⏭️", "⏮️", "⏩", "⏪", "⏫", "⏬", "◀️",
                "🔼", "🔽", "➡️", "⬅️", "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️",
                "↕️", "↔️", "↪️", "↩️", "⤴️", "⤵️", "🔀", "🔁", "🔂", "🔄",
                "🔃", "🎵", "🎶", "➕", "➖", "➗", "✖️", "♾️", "💲", "💱",
                "™️", "©️", "®️", "〰️", "➰", "➿", "🔚", "🔙", "🔛", "🔝",
                "🔜", "✔️", "☑️", "🔘", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣",
                "⚫", "⚪", "🟤", "🔺", "🔻", "🔸", "🔹", "🔶", "🔷", "🔳",
                "🔲", "▪️", "▫️", "◾", "◽", "◼️", "◻️", "🟥", "🟧", "🟨",
                "🟩", "🟦", "🟪", "⬛", "⬜", "🟫", "🔈", "🔇", "🔉", "🔊",
                "🔔", "🔕", "📣", "📢", "👁️‍🗨️", "💬", "💭", "🗯️", "♠️", "♣️",
                "♥️", "♦️", "🃏", "🎴", "🀄", "🕐", "🕑", "🕒", "🕓", "🕔",
                "🕕", "🕖", "🕗", "🕘", "🕙", "🕚", "🕛"
            )
        ),
        EmojiCategory(
            name = "Flags",
            icon = "🏳️",
            emojis = listOf(
                "🏳️", "🏴", "🏴‍☠️", "🏁", "🚩", "🏳️‍🌈", "🏳️‍⚧️", "🇺🇳",
                "🇦🇫", "🇦🇱", "🇩🇿", "🇦🇸", "🇦🇩", "🇦🇴", "🇦🇮", "🇦🇶",
                "🇦🇬", "🇦🇷", "🇦🇲", "🇦🇼", "🇦🇺", "🇦🇹", "🇦🇿", "🇧🇸",
                "🇧🇭", "🇧🇩", "🇧🇧", "🇧🇾", "🇧🇪", "🇧🇿", "🇧🇯", "🇧🇲",
                "🇧🇹", "🇧🇴", "🇧🇦", "🇧🇼", "🇧🇷", "🇻🇬", "🇧🇳", "🇧🇬",
                "🇧🇫", "🇧🇮", "🇰🇭", "🇨🇲", "🇨🇦", "🇨🇻", "🇰🇾", "🇨🇫",
                "🇹🇩", "🇨🇱", "🇨🇳", "🇨🇴", "🇰🇲", "🇨🇬", "🇨🇩", "🇨🇷",
                "🇭🇷", "🇨🇺", "🇨🇾", "🇨🇿", "🇩🇰", "🇩🇯", "🇩🇲", "🇩🇴",
                "🇪🇨", "🇪🇬", "🇸🇻", "🇬🇶", "🇪🇷", "🇪🇪", "🇪🇹", "🇫🇯",
                "🇫🇮", "🇫🇷", "🇬🇦", "🇬🇲", "🇬🇪", "🇩🇪", "🇬🇭", "🇬🇷",
                "🇬🇩", "🇬🇺", "🇬🇹", "🇬🇳", "🇬🇾", "🇭🇹", "🇭🇳", "🇭🇰",
                "🇭🇺", "🇮🇸", "🇮🇳", "🇮🇩", "🇮🇷", "🇮🇶", "🇮🇪", "🇮🇱",
                "🇮🇹", "🇯🇲", "🇯🇵", "🇯🇴", "🇰🇿", "🇰🇪", "🇰🇮", "🇰🇵",
                "🇰🇷", "🇰🇼", "🇰🇬", "🇱🇦", "🇱🇻", "🇱🇧", "🇱🇸", "🇱🇷",
                "🇱🇾", "🇱🇮", "🇱🇹", "🇱🇺", "🇲🇴", "🇲🇬", "🇲🇼", "🇲🇾",
                "🇲🇻", "🇲🇱", "🇲🇹", "🇲🇭", "🇲🇷", "🇲🇺", "🇲🇽", "🇫🇲",
                "🇲🇩", "🇲🇨", "🇲🇳", "🇲🇪", "🇲🇦", "🇲🇿", "🇲🇲", "🇳🇦",
                "🇳🇷", "🇳🇵", "🇳🇱", "🇳🇿", "🇳🇮", "🇳🇪", "🇳🇬", "🇳🇴",
                "🇴🇲", "🇵🇰", "🇵🇼", "🇵🇸", "🇵🇦", "🇵🇬", "🇵🇾", "🇵🇪",
                "🇵🇭", "🇵🇱", "🇵🇹", "🇵🇷", "🇶🇦", "🇷🇴", "🇷🇺", "🇷🇼",
                "🇼🇸", "🇸🇲", "🇸🇦", "🇸🇳", "🇷🇸", "🇸🇨", "🇸🇱", "🇸🇬",
                "🇸🇰", "🇸🇮", "🇸🇧", "🇸🇴", "🇿🇦", "🇸🇸", "🇪🇸", "🇱🇰",
                "🇸🇩", "🇸🇷", "🇸🇪", "🇨🇭", "🇸🇾", "🇹🇼", "🇹🇯", "🇹🇿",
                "🇹🇭", "🇹🇱", "🇹🇬", "🇹🇴", "🇹🇹", "🇹🇳", "🇹🇷", "🇹🇲",
                "🇺🇬", "🇺🇦", "🇦🇪", "🇬🇧", "🇺🇸", "🇺🇾", "🇺🇿", "🇻🇺",
                "🇻🇦", "🇻🇪", "🇻🇳", "🇾🇪", "🇿🇲", "🇿🇼"
            )
        )
    )

    /**
     * Get all emojis for a specific category index.
     */
    fun getEmojisForCategory(index: Int): List<String> {
        return categories.getOrNull(index)?.emojis ?: emptyList()
    }

    /**
     * Get category icons for tab display.
     */
    fun getCategoryIcons(): List<String> {
        return categories.map { it.icon }
    }

    /**
     * Get total number of categories.
     */
    fun getCategoryCount(): Int = categories.size
}
// Build trigger 1775110747
