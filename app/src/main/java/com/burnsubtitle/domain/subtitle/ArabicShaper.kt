package com.burnsubtitle.domain.subtitle

/**
 * Maps logical Arabic letters to Unicode presentation forms.
 *
 * FFmpeg/libass still receives **logical** UTF-8. HarfBuzz in the bundled
 * libass performs OpenType shaping against Noto Naskh Arabic. This shaper is
 * the explicit joining implementation (lam-alef, initial/medial/final) used
 * for tests and any renderer that lacks HarfBuzz.
 */
object ArabicShaper {
    fun shape(text: String): String {
        if (!ScriptClassifier.containsArabic(text)) return text
        val out = StringBuilder(text.length)
        ScriptClassifier.splitRuns(text).forEach { run ->
            if (run.kind == ScriptKind.ARABIC) {
                out.append(shapeRun(run.text))
            } else {
                out.append(run.text)
            }
        }
        return out.toString()
    }

    private fun shapeRun(text: String): String {
        val codes = text.toCodePointArray()
        val out = StringBuilder(codes.size)
        var index = 0
        while (index < codes.size) {
            val code = codes[index]
            if (ArabicJoining.type(code) == JoiningType.TRANSPARENT || ArabicJoining.type(code) == JoiningType.NONE) {
                out.appendCodePoint(code)
                index++
                continue
            }
            if (code == LAM) {
                val next = nextLetter(codes, index + 1)
                val ligature = lamAlef(next?.let { codes[it] })
                if (ligature != 0) {
                    val joinsPrev = previousLetter(codes, index - 1)?.let {
                        ArabicJoining.type(codes[it]) == JoiningType.DUAL
                    } == true
                    out.appendCodePoint(if (joinsPrev) ligature + 1 else ligature)
                    val skipTo = next!!
                    for (diacritic in (index + 1) until skipTo) {
                        out.appendCodePoint(codes[diacritic])
                    }
                    index = skipTo + 1
                    continue
                }
            }
            val prev = previousLetter(codes, index - 1)
            val next = nextLetter(codes, index + 1)
            val joinsPrev = prev != null && canJoinForward(codes[prev]) && canJoinBackward(code)
            val joinsNext = next != null && canJoinForward(code) && canJoinBackward(codes[next])
            val form = when {
                joinsPrev && joinsNext -> Form.MEDIAL
                joinsPrev -> Form.FINAL
                joinsNext -> Form.INITIAL
                else -> Form.ISOLATED
            }
            out.appendCodePoint(presentation(code, form))
            index++
        }
        return out.toString()
    }

    private fun String.toCodePointArray(): IntArray {
        val codes = ArrayList<Int>(length)
        var i = 0
        while (i < length) {
            val code = codePointAt(i)
            codes += code
            i += Character.charCount(code)
        }
        return codes.toIntArray()
    }

    private fun canJoinForward(code: Int): Boolean {
        return ArabicJoining.type(code) == JoiningType.DUAL
    }

    private fun canJoinBackward(code: Int): Boolean {
        val type = ArabicJoining.type(code)
        return type == JoiningType.DUAL || type == JoiningType.RIGHT
    }

    private fun previousLetter(codes: IntArray, start: Int): Int? {
        var i = start
        while (i >= 0) {
            when (ArabicJoining.type(codes[i])) {
                JoiningType.TRANSPARENT -> i--
                else -> return i
            }
        }
        return null
    }

    private fun nextLetter(codes: IntArray, start: Int): Int? {
        var i = start
        while (i < codes.size) {
            when (ArabicJoining.type(codes[i])) {
                JoiningType.TRANSPARENT -> i++
                else -> return i
            }
        }
        return null
    }

    private fun lamAlef(next: Int?): Int {
        return when (next) {
            0x0627 -> 0xFEFB
            0x0623 -> 0xFEF7
            0x0625 -> 0xFEF9
            0x0622 -> 0xFEF5
            else -> 0
        }
    }

    private fun presentation(code: Int, form: Form): Int {
        val forms = FORMS[code] ?: return code
        return forms[form.ordinal]
    }

    private enum class Form { ISOLATED, FINAL, INITIAL, MEDIAL }

    private const val LAM = 0x0644

    private val FORMS = mapOf(
        0x0627 to intArrayOf(0xFE8D, 0xFE8E, 0xFE8D, 0xFE8E),
        0x0622 to intArrayOf(0xFE81, 0xFE82, 0xFE81, 0xFE82),
        0x0623 to intArrayOf(0xFE83, 0xFE84, 0xFE83, 0xFE84),
        0x0625 to intArrayOf(0xFE87, 0xFE88, 0xFE87, 0xFE88),
        0x0628 to intArrayOf(0xFE8F, 0xFE90, 0xFE91, 0xFE92),
        0x062A to intArrayOf(0xFE95, 0xFE96, 0xFE97, 0xFE98),
        0x062B to intArrayOf(0xFE99, 0xFE9A, 0xFE9B, 0xFE9C),
        0x062C to intArrayOf(0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0),
        0x062D to intArrayOf(0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4),
        0x062E to intArrayOf(0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8),
        0x062F to intArrayOf(0xFEA9, 0xFEAA, 0xFEA9, 0xFEAA),
        0x0630 to intArrayOf(0xFEAB, 0xFEAC, 0xFEAB, 0xFEAC),
        0x0631 to intArrayOf(0xFEAD, 0xFEAE, 0xFEAD, 0xFEAE),
        0x0632 to intArrayOf(0xFEAF, 0xFEB0, 0xFEAF, 0xFEB0),
        0x0633 to intArrayOf(0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4),
        0x0634 to intArrayOf(0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8),
        0x0635 to intArrayOf(0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC),
        0x0636 to intArrayOf(0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0),
        0x0637 to intArrayOf(0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4),
        0x0638 to intArrayOf(0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8),
        0x0639 to intArrayOf(0xFEC9, 0xFECA, 0xFECB, 0xFECC),
        0x063A to intArrayOf(0xFECD, 0xFECE, 0xFECF, 0xFED0),
        0x0641 to intArrayOf(0xFED1, 0xFED2, 0xFED3, 0xFED4),
        0x0642 to intArrayOf(0xFED5, 0xFED6, 0xFED7, 0xFED8),
        0x0643 to intArrayOf(0xFED9, 0xFEDA, 0xFEDB, 0xFEDC),
        0x0644 to intArrayOf(0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0),
        0x0645 to intArrayOf(0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4),
        0x0646 to intArrayOf(0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8),
        0x0647 to intArrayOf(0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC),
        0x0648 to intArrayOf(0xFEED, 0xFEEE, 0xFEED, 0xFEEE),
        0x064A to intArrayOf(0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4),
        0x0649 to intArrayOf(0xFEEF, 0xFEF0, 0xFEEF, 0xFEF0),
        0x0629 to intArrayOf(0xFE93, 0xFE94, 0xFE93, 0xFE94),
        0x0626 to intArrayOf(0xFE89, 0xFE8A, 0xFE8B, 0xFE8C),
        0x0624 to intArrayOf(0xFE85, 0xFE86, 0xFE85, 0xFE86),
    )
}
