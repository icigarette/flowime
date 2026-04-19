package io.justcodeit.smartime.model

data class ContextSnapshot(
    val fileType: String,
    val language: String,
    val offset: Int,
    val contextType: ContextType,
    val psiElementType: String,
    val timestamp: Long,
) {
    fun dedupeKey(): String = "$fileType|$language|$contextType|$psiElementType"
}

