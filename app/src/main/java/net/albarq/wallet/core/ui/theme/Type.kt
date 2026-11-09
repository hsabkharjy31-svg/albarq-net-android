package net.albarq.wallet.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import net.albarq.wallet.R

private val NotoSansArabic = FontFamily(
    Font(R.font.noto_sans_arabic, FontWeight.Normal)
)

private val NotoNaskhArabic = FontFamily(
    Font(R.font.noto_naskh_arabic, FontWeight.Normal)
)

private val BaseTypography = Typography()

val AlbarqTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = NotoSansArabic),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = NotoSansArabic),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = NotoSansArabic),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = NotoSansArabic),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = NotoSansArabic),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = NotoSansArabic),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = NotoSansArabic),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = NotoSansArabic),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = NotoNaskhArabic),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = NotoNaskhArabic),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = NotoNaskhArabic),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = NotoSansArabic),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = NotoSansArabic),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = NotoSansArabic)
)
