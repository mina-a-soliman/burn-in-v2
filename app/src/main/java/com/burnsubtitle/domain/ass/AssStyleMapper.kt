package com.burnsubtitle.domain.ass

import com.burnsubtitle.domain.model.SubtitleStyle
import javax.inject.Inject
import javax.inject.Singleton

@Deprecated("Use AssStyleGenerator")
@Singleton
class AssStyleMapper @Inject constructor(
    private val generator: AssStyleGenerator,
) {
    fun toStyleLine(style: SubtitleStyle, playResY: Int): String {
        return generator.toStyleLine(style, playResY, playResY)
    }
}
