package ir.mtlink.client

class UiText(private val language: AppLanguage) {
    fun of(fa: String, en: String): String = if (language == AppLanguage.FA) fa else en
    val isRtl: Boolean get() = language == AppLanguage.FA
}
