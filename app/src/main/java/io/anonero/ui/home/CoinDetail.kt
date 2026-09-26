package io.anonero.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
                        .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !current.frozen,
                        onClick = { actionFailed = !walletState.freezeCoin(current) }
                    ) { Text("冻结") }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = current.frozen,
                        onClick = { actionFailed = !walletState.thawCoin(current) }
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("金额", style = MaterialTheme.typography.labelMedium)
                        Text(
                            Formats.getDisplayAmount(coin.amount),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                supportingContent = {
                    Column {
                        Text("状态：" + if (coin.frozen) "已冻结" else "未冻结")
                        Spacer(Modifier.height(12.dp))
                        Text("公钥")
                        Text(coin.pub_key, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("Key Image")
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
