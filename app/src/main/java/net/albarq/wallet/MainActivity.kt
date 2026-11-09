package net.albarq.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint
import net.albarq.wallet.core.ui.theme.AlbarqBlue
import net.albarq.wallet.core.ui.theme.AlbarqBlueDark
import net.albarq.wallet.core.ui.theme.AlbarqMuted
import net.albarq.wallet.core.ui.theme.AlbarqTheme

private enum class MainTab(val label: String, val symbol: String) {
    HOME("الرئيسية", "⌂"),
    PACKAGES("الباقات", "▦"),
    CARDS("بطاقاتي", "▤"),
    MORE("المزيد", "•••")
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlbarqTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlbarqAppScreen()
                }
            }
        }
    }
}

@Composable
private fun AlbarqAppScreen() {
    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
    var moreDetail by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = moreDetail != null) { moreDetail = null }

    Scaffold(
        containerColor = Color(0xFFF5F7FB),
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                MainTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            moreDetail = null
                        },
                        icon = {
                            Text(
                                text = tab.symbol,
                                fontSize = if (tab == MainTab.MORE) 17.sp else 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        label = { Text(tab.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AlbarqBlue,
                            selectedTextColor = AlbarqBlue,
                            indicatorColor = AlbarqBlue.copy(alpha = 0.10f),
                            unselectedIconColor = AlbarqMuted,
                            unselectedTextColor = AlbarqMuted
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 24.dp)
        ) {
            when (selectedTab) {
                MainTab.HOME -> HomePage(
                    onPackages = { selectedTab = MainTab.PACKAGES },
                    onCards = { selectedTab = MainTab.CARDS },
                    onMoreDetail = {
                        selectedTab = MainTab.MORE
                        moreDetail = it
                    }
                )
                MainTab.PACKAGES -> EmptyPage(
                    title = "باقات الإنترنت",
                    description = "ستظهر هنا الباقات المعتمدة وأسعارها ومددها بعد ربط التطبيق بخدمة البرق نت.",
                    symbol = "▦",
                    action = "العودة للرئيسية",
                    onAction = { selectedTab = MainTab.HOME }
                )
                MainTab.CARDS -> EmptyPage(
                    title = "بطاقاتي",
                    description = "بعد تسجيل الدخول ستظهر بطاقاتك المشتراة هنا، مع إمكانية فتح تفاصيل البطاقة عند الحاجة.",
                    symbol = "▤",
                    action = "استعراض الباقات",
                    onAction = { selectedTab = MainTab.PACKAGES }
                )
                MainTab.MORE -> {
                    if (moreDetail == null) {
                        MorePage(onSelect = { moreDetail = it })
                    } else {
                        EmptyPage(
                            title = moreDetail.orEmpty(),
                            description = moreDetailDescription(moreDetail.orEmpty()),
                            symbol = "◈",
                            action = "العودة",
                            onAction = { moreDetail = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    onPackages: () -> Unit,
    onCards: () -> Unit,
    onMoreDetail: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(AlbarqBlue, AlbarqBlueDark))),
                contentAlignment = Alignment.Center
            ) {
                Text("ب", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Text("البرق نت", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
                Text("خدمات الإنترنت بين يديك", fontSize = 12.sp, color = AlbarqMuted)
            }
        }
        Surface(
            modifier = Modifier
                .size(44.dp)
                .clickable { onMoreDetail("الإشعارات") },
            shape = RoundedCornerShape(14.dp),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("♧", color = AlbarqBlue, fontSize = 23.sp)
            }
        }
    }

    Spacer(Modifier.height(26.dp))
    Text("مرحباً بك 👋", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
    Spacer(Modifier.height(4.dp))
    Text("تابع محفظتك واشترِ بطاقات الإنترنت بسهولة.", fontSize = 14.sp, color = AlbarqMuted)

    Spacer(Modifier.height(20.dp))
    WalletBalanceCard(onTopUp = { onMoreDetail("شحن المحفظة") })

    Spacer(Modifier.height(24.dp))
    SectionTitle("الخدمات السريعة", "اختر الخدمة التي تحتاجها")
    Spacer(Modifier.height(13.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        FeatureTile(
            modifier = Modifier.weight(1f),
            symbol = "＋",
            title = "شراء بطاقة",
            subtitle = "اختر باقتك",
            tint = Color(0xFF0B63F6),
            onClick = onPackages
        )
        FeatureTile(
            modifier = Modifier.weight(1f),
            symbol = "▤",
            title = "بطاقاتي",
            subtitle = "البطاقات المشتراة",
            tint = Color(0xFF0D9488),
            onClick = onCards
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        FeatureTile(
            modifier = Modifier.weight(1f),
            symbol = "↗",
            title = "شحن المحفظة",
            subtitle = "إرسال طلب شحن",
            tint = Color(0xFFB7791F),
            onClick = { onMoreDetail("شحن المحفظة") }
        )
        FeatureTile(
            modifier = Modifier.weight(1f),
            symbol = "◷",
            title = "سجل العمليات",
            subtitle = "مشترياتك وطلباتك",
            tint = Color(0xFF7C3AED),
            onClick = { onMoreDetail("سجل العمليات") }
        )
    }

    Spacer(Modifier.height(24.dp))
    SectionTitle("باقات الإنترنت", "تصفح الخيارات المتاحة")
    Spacer(Modifier.height(12.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPackages),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFEAF1FF)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 24.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("اكتشف الباقات", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
                Text("اعرف تفاصيل الباقات وأسعارها", fontSize = 12.sp, color = AlbarqMuted)
            }
            Text("‹", fontSize = 28.sp, color = AlbarqBlue)
        }
    }

    Spacer(Modifier.height(24.dp))
    SectionTitle("آخر العمليات", "ملخص نشاط حسابك")
    Spacer(Modifier.height(12.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("◷", fontSize = 30.sp, color = AlbarqMuted)
            Spacer(Modifier.height(7.dp))
            Text("لا توجد عمليات معروضة", fontWeight = FontWeight.SemiBold, color = Color(0xFF344054))
            Text(
                "سجّل الدخول لعرض سجل مشترياتك وطلباتك الفعلية.",
                fontSize = 12.sp,
                color = AlbarqMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WalletBalanceCard(onTopUp: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF1268F3), Color(0xFF073E9E))))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("رصيد المحفظة", color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp)
                Text("◈", color = Color.White, fontSize = 22.sp)
            }
            Spacer(Modifier.height(9.dp))
            Text("— — —", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("الرصيد الفعلي يظهر بعد تسجيل الدخول", color = Color.White.copy(alpha = 0.80f), fontSize = 11.sp)
            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onTopUp),
                color = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("＋", fontSize = 17.sp, color = AlbarqBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(7.dp))
                    Text("شحن المحفظة", color = AlbarqBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = AlbarqMuted)
    }
}

@Composable
private fun FeatureTile(
    modifier: Modifier,
    symbol: String,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(19.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(tint.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Text(symbol, color = tint, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = AlbarqMuted)
        }
    }
}

@Composable
private fun MorePage(onSelect: (String) -> Unit) {
    PageHeading("المزيد", "إدارة حسابك وخدمات التطبيق")
    Spacer(Modifier.height(18.dp))
    val rows = listOf(
        Triple("شحن المحفظة", "إرسال طلب شحن لمراجعة الإدارة", "＋"),
        Triple("سجل العمليات", "عرض سجل المشتريات والطلبات", "◷"),
        Triple("الإشعارات", "متابعة آخر التنبيهات", "♧"),
        Triple("الدعم الفني", "التواصل مع فريق المساعدة", "؟"),
        Triple("الملف الشخصي", "بيانات حسابك", "♙"),
        Triple("الإعدادات", "تفضيلات التطبيق والأمان", "⚙")
    )
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column {
            rows.forEachIndexed { index, row ->
                MoreRow(row.first, row.second, row.third) { onSelect(row.first) }
                if (index != rows.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp, end = 16.dp)
                            .height(1.dp)
                            .background(Color(0xFFF0F2F5))
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, symbol: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEAF1FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, fontSize = 19.sp, color = AlbarqBlue)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF172033))
            Text(subtitle, fontSize = 11.sp, color = AlbarqMuted)
        }
        Text("‹", fontSize = 25.sp, color = AlbarqMuted)
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
    Spacer(Modifier.height(4.dp))
    Text(subtitle, fontSize = 13.sp, color = AlbarqMuted)
}

@Composable
private fun EmptyPage(
    title: String,
    description: String,
    symbol: String,
    action: String,
    onAction: () -> Unit
) {
    PageHeading(title, "البرق نت")
    Spacer(Modifier.height(42.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Color(0xFFEAF1FF)),
            contentAlignment = Alignment.Center
        ) {
            Text(symbol, fontSize = 35.sp, color = AlbarqBlue)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172033))
        Spacer(Modifier.height(8.dp))
        Text(description, fontSize = 13.sp, lineHeight = 22.sp, color = AlbarqMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .clickable(onClick = onAction),
            shape = RoundedCornerShape(13.dp),
            color = AlbarqBlue
        ) {
            Text(
                action,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

private fun moreDetailDescription(title: String): String = when (title) {
    "شحن المحفظة" -> "تُعرض هنا خطوات إرسال طلب الشحن وإرفاق الإيصال. لن يتم اعتماد أي رصيد قبل مراجعة الإدارة والتحقق من الطلب."
    "سجل العمليات" -> "سيتم عرض العمليات المؤكدة من الخادم فقط بعد تسجيل الدخول وربط واجهة الخدمة."
    "الإشعارات" -> "ستظهر هنا إشعارات الحساب والطلبات عند تفعيل خدمة الإشعارات."
    "الدعم الفني" -> "ستتوفر قنوات الدعم الرسمية وبيانات التواصل بعد اعتمادها من إدارة الخدمة."
    "الملف الشخصي" -> "ستظهر بيانات الحساب بعد تسجيل الدخول."
    "الإعدادات" -> "إعدادات الحساب والخصوصية والأمان ستتوفر بعد ربط خدمات الحساب."
    else -> "هذه الخدمة تحتاج إلى ربط واجهة البرق نت قبل عرض بيانات فعلية."
}
