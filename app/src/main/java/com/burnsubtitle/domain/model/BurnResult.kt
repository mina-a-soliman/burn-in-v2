package com.burnsubtitle.domain.model

sealed class BurnResult {
    data class Success(
        val outputUri: String,
        val outputPath: String,
        val displayName: String,
    ) : BurnResult()

    data class Failure(
        val message: String,
        val exitCode: Int? = null,
        val details: String? = null,
    ) : BurnResult()

    data object Cancelled : BurnResult()
}
