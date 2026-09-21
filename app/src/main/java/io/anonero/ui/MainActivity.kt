package io.anonero.ui

import AnonNeroTheme
import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.asLiveData
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import io.anonero.AnonConfig
import io.anonero.model.WalletManager
import io.anonero.services.AnonNeroService
import io.anonero.services.TorService
import io.anonero.services.WalletState
import io.anonero.services.startAnonService
import io.anonero.ui.home.LockScreen
import io.anonero.ui.home.LockScreenMode
import io.anonero.ui.home.LockScreenShortCut
import io.anonero.ui.home.graph.homeGraph
import io.anonero.ui.home.graph.routes.Home
import io.anonero.ui.onboard.OnboardViewModel
import io.anonero.ui.onboard.graph.LandingScreenRoute
import io.anonero.ui.onboard.graph.onboardingGraph
import io.anonero.util.WALLET_PREFERENCES
import io.anonero.util.WALLET_USE_TOR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.core.qualifier.named
import timber.log.Timber


private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val walletState: WalletState by inject()
    private val torService: TorService by inject()
    val anonPrefs: SharedPreferences by inject(named(WALLET_PREFERENCES))

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashscreen = installSplashScreen()
        var isAppReady by mutableStateOf(false)
        super.onCreate(savedInstanceState)

        val scrimColor = Color.Black.copy(alpha = 0.1f).toArgb()
        splashscreen.setKeepOnScreenCondition { isAppReady }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrimColor),
            navigationBarStyle = SystemBarStyle.dark(scrimColor),
        )
        setContent {
            var walletExist by remember { mutableStateOf(AnonConfig.isWalletFileExist()) }
            var crashLog by remember { mutableStateOf<String?>(null) }
            var useTor by remember { mutableStateOf(true) }
            val onboardViewModel = koinViewModel<OnboardViewModel>()
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            LaunchedEffect(key1 = true) {
                scope.launch(Dispatchers.IO) {
                    walletExist = AnonConfig.getDefaultWalletFile(context).exists()
                    val logFile = AnonConfig.getLogFile(context)
                    if (logFile.exists()) {
                        val text = logFile.readText()
                        val marker = "=== UNCAUGHT EXCEPTION ==="
                        val index = text.lastIndexOf(marker)
                        if (index >= 0) crashLog = text.substring(index)
                    }
                    useTor = anonPrefs.getBoolean(WALLET_USE_TOR, true)
                    isAppReady = true
                }
            }
            val clipboardManager = LocalClipboardManager.current
            crashLog?.let { log ->
                AlertDialog(
                    onDismissRequest = { crashLog = null },
                    title = { Text("上次闪退日志") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(log)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(log))
                        }) { Text("复制") }
                    },
                    dismissButton = {
                        TextButton(onClick = { crashLog = null }) { Text("关闭") }
                    }
                )
            }
            TorSplash(enableTor = useTor) {
                AnonNeroTheme {
                    val navController = rememberNavController()
                    navController.addOnDestinationChangedListener { x, destination, d ->
                        Timber.tag(TAG).d("destination changed to ${destination.route}")
                    }
                    val showLockScreen by walletState.backgroundSyncFlow.asLiveData().observeAsState(walletState.backgroundSync)
                    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController, popExitTransition = {
                            ExitTransition.None
                        }, popEnterTransition = {
                            EnterTransition.None
                        }, startDestination = if (walletExist) Home else LandingScreenRoute
                        ) {
                            //landing screen and onboarding screens
                            onboardingGraph(navController, onboardViewModel)
                            //home bottom navigation
                            homeGraph(navController)
                        }
                        if (showLockScreen) {
                            LockScreen(
                                mode = LockScreenMode.LOCK_SCREEN,
                                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                onUnLocked = { pin, shortCut ->
                                    if (shortCut != LockScreenShortCut.HOME) {
                                        walletState.emitUnlockShortcut(shortCut)
                                        scope.launch {
                                            kotlinx.coroutines.delay(250)
                                            walletState.setBackGroundSync(false)
                                        }
                                    } else {
                                        walletState.setBackGroundSync(false)
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        walletState.exitBackgroundSync(pin)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Intent(applicationContext, AnonNeroService::class.java).also {
                it.action = "start"
                ContextCompat.startForegroundService(applicationContext, it)
            }
        }
        //handle and show dialog
    }

    fun startNotificationService() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startAnonService(applicationContext)
        }
    }

    fun stopNotificationService() {
        Intent(applicationContext, AnonNeroService::class.java).also {
            it.action = "stop"
            ContextCompat.startForegroundService(applicationContext, it)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        scope.launch { walletState.enterBackgroundSync() }
    }

    override fun onDestroy() {
        scope.launch(Dispatchers.IO) {
            WalletManager.instance?.wallet?.let {
                it.store()
                it.close()
            }
        }.invokeOnCompletion {
            if (it != null) {
                Timber.tag(TAG).e(it)
            }
        }
        torService.dispose()
        scope.cancel()
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }
}
