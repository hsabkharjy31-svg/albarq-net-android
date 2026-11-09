package net.albarq.wallet.core.ui.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
private val AlbarqScheme=lightColorScheme(primary=AlbarqBlue,onPrimary=AlbarqSurface,secondary=AlbarqAccent,background=AlbarqBackground,onBackground=AlbarqText,surface=AlbarqSurface,onSurface=AlbarqText,onSurfaceVariant=AlbarqMuted,error=AlbarqDanger)
@Composable fun AlbarqTheme(content:@Composable ()->Unit) { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { MaterialTheme(colorScheme=AlbarqScheme,typography=AlbarqTypography,content=content) } }
