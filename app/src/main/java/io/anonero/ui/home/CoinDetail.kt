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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val success = if (current.frozen) {
                                walletState.thawCoin(current)
                            } else {
                                walletState.freezeCoin(current)
                            }
                            actionFailed = !success
                        }
                    ) {
                        Text(if (current.frozen) "解冻" else "冻结")
                    }
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

            Text(
                "金额",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                Formats.getDisplayAmount(coin.amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(24.dp))

            Text(
                "状态",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                if (coin.frozen) "已冻结" else "未冻结",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(24.dp))

            Text(
                "公钥",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                coin.pub_key,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))

            Text(
                "Key Image",
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                coin.key,
                style = MaterialTheme.typography.bodyMedium
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
