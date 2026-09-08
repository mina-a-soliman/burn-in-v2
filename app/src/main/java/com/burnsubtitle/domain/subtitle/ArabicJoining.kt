package com.burnsubtitle.domain.subtitle

enum class JoiningType {
    DUAL,
    RIGHT,
    NONE,
    TRANSPARENT,
}

object ArabicJoining {
    fun type(code: Int): JoiningType {
        if (isTransparent(code)) return JoiningType.TRANSPARENT
        if (code in RIGHT_JOINING) return JoiningType.RIGHT
        if (code in DUAL_JOINING) return JoiningType.DUAL
        return JoiningType.NONE
    }

    fun isTransparent(code: Int): Boolean {
        return code in 0x064B..0x065F ||
            code == 0x0670 ||
            code == 0x0640 ||
            code in 0x06D6..0x06ED ||
            code in 0x08D3..0x08FF
    }

    private val RIGHT_JOINING = setOf(
        0x0622, 0x0623, 0x0625, 0x0627, 0x0629, 0x062F, 0x0630, 0x0631, 0x0632,
        0x0648, 0x0624, 0x0649, 0x0671, 0x06BA,
    )

    private val DUAL_JOINING = setOf(
        0x0626, 0x0628, 0x062A, 0x062B, 0x062C, 0x062D, 0x062E, 0x0633, 0x0634,
        0x0635, 0x0636, 0x0637, 0x0638, 0x0639, 0x063A, 0x0641, 0x0642, 0x0643,
        0x0644, 0x0645, 0x0646, 0x0647, 0x064A, 0x06CC, 0x06A9, 0x06AF, 0x067E,
        0x0686, 0x0698, 0x06BE,
    )
}
