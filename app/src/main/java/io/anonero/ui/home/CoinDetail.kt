package io.anonero.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.anonero.model.CoinsInfo
import io.anonero.services.WalletState
import io.anonero.util.Formats
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(
    keyImage: String,
    onBackPress: () -> Unit = {},
) {
    val walletState = koinInject<WalletState>()
    val coins by walletState.coins.collectAsState(emptyList())
    val coin: CoinsInfo? = coins.firstOrNull { it.key == keyImage }
    var actionFailed by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("输出详情") },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            coin?.let { current ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !current.frozen,
                        onClick = { actionFailed = !walletState.freezeCoin(current) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(12.dp)
                    ) { Text("冻结") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = current.frozen,
                        onClick = { actionFailed = !walletState.thawCoin(current) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(12.dp)
                    ) { Text("解冻") }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            if (coin == null) {
                Text("输出不存在")
                return@Column
            }

            ListItem(
                headlineContent = {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val amountColor = if (coin.frozen) {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                        Text("金额", color = amountColor)
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                Formats.getDisplayAmount(coin.amount),
                                color = amountColor,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            )

            ListItem(
                supportingContent = {
                    Column {
                        Text("公钥")
                        Text(coin.pub_key, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("密钥图像")
                        Text(coin.key, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            )

            if (actionFailed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "操作失败",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
