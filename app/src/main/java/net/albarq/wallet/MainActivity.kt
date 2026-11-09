package net.albarq.wallet
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import net.albarq.wallet.core.ui.theme.AlbarqTheme
@AndroidEntryPoint class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent { AlbarqTheme { Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){ StarterScreen() } } } } }
@Composable private fun StarterScreen(){ Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){ Text("ب",style=MaterialTheme.typography.displayLarge,color=MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.app_name_ar),style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold); Spacer(Modifier.height(20.dp)); Text(stringResource(R.string.phase_one_description),textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyLarge) } }
