package com.anybuilder.findphone.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huawei.agconnect.AGConnectInstance
import com.huawei.agconnect.AGConnectOptionsBuilder
import com.huawei.hmf.tasks.OnFailureListener
import com.huawei.hmf.tasks.OnSuccessListener
import com.huawei.hmf.tasks.Task
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.push.HmsMessaging
import com.anybuilder.findphone.data.MonitoringConfig
import com.anybuilder.findphone.data.MonitoringState
import com.anybuilder.findphone.data.RingtoneInfo
import com.anybuilder.findphone.service.AudioPlayer
import com.anybuilder.findphone.service.MonitoringService
import com.anybuilder.findphone.ui.theme.MonitorAlertTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.anybuilder.findphone.R

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startMonitoring()
        }
    }

    private lateinit var audioPlayer: AudioPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioPlayer = AudioPlayer(this)

        requestBatteryOptimizationExemption()

        AGConnectInstance.initialize(this)
        initializeHmsPush()

        setContent {
            MonitorAlertTheme {
                val viewModel: MainViewModel = viewModel()
                val config by viewModel.config.collectAsState()
                val state by viewModel.state.collectAsState()
                val pushToken by viewModel.pushToken.collectAsState()
                val isActivated by viewModel.isActivated.collectAsState()
                val isActivating by viewModel.isActivating.collectAsState()
                val activationError by viewModel.activationError.collectAsState()
                val deviceId by viewModel.deviceId.collectAsState()

                val ringtones = remember { getAvailableRingtones() }

                MainScreen(
                    config = config,
                    state = state,
                    pushToken = pushToken,
                    ringtones = ringtones,
                    isActivated = isActivated,
                    isActivating = isActivating,
                    activationError = activationError,
                    deviceId = deviceId,
                    onConfigChange = viewModel::updateConfig,
                    onPreviewRingtone = { uri, volume ->
                        audioPlayer.previewRingtone(uri, volume)
                    },
                    onActivate = viewModel::activate
                )
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request battery optimization exemption", e)
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                } catch (e2: Exception) {
                    Log.w(TAG, "Failed to open battery optimization settings", e2)
                }
            }
        }
    }

private fun initializeHmsPush() {
        try {
            val messaging = HmsMessaging.getInstance(this)
            messaging.turnOnPush().addOnSuccessListener {
                Log.i(TAG, "HMS Push turned on successfully")
                Log.i(TAG, "Attempting to get token in background thread...")
                
                Thread {
                    try {
                        @Suppress("DEPRECATION")
                        val token = HmsInstanceId.getInstance(this).getToken()
                        if (!token.isNullOrEmpty()) {
                            Log.i(TAG, "Token obtained in background: ${token}")
                            runOnUiThread {
                                saveTokenToPrefs(token)
                            }
                        } else {
                            Log.w(TAG, "Token is null or empty, waiting for callback")
                            runOnUiThread {
                                Log.i(TAG, "Waiting for token via onNewToken callback in HmsMessagingService")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get token in background: ${e.message}")
                        runOnUiThread {
                            Log.i(TAG, "Waiting for token via onNewToken callback in HmsMessagingService")
                        }
                    }
                }.start()
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to turn on HMS Push: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "HMS Push initialization error: ${e.message}")
        }
    }
    
    private fun saveTokenToPrefs(token: String) {
        val sharedPrefs = getSharedPreferences("monitor_alert_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("hms_push_token", token).apply()
        Log.i(TAG, "Token saved to SharedPreferences")
    }

    private fun getAvailableRingtones(): List<RingtoneInfo> {
        val ringtones = mutableListOf<RingtoneInfo>()

        try {
            val ringtoneManager = RingtoneManager(this)
            ringtoneManager.setType(RingtoneManager.TYPE_ALARM)

            val cursor = ringtoneManager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = ringtoneManager.getRingtoneUri(cursor.position)
                ringtones.add(RingtoneInfo(
                    name = title,
                    uri = uri.toString()
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (ringtones.isEmpty()) {
            ringtones.add(RingtoneInfo(
                name = getString(R.string.default_alarm),
                uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
            ))
        }

        return ringtones
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startMonitoring() {
        MonitoringService.startService(this)
    }

    override fun onResume() {
        super.onResume()
        if (hasNotificationPermission()) {
            startMonitoring()
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun MainScreen(
    config: MonitoringConfig,
    state: MonitoringState,
    pushToken: String?,
    ringtones: List<RingtoneInfo>,
    isActivated: Boolean,
    isActivating: Boolean,
    activationError: String?,
    deviceId: String?,
    onConfigChange: (MonitoringConfig) -> Unit,
    onPreviewRingtone: (String, Float) -> Unit,
    onActivate: (String) -> Unit
) {
    var volume by remember { mutableStateOf(config.volumeLevel) }
    var vibrateEnabled by remember { mutableStateOf(config.vibrateEnabled) }
    var ringtoneExpanded by remember { mutableStateOf(false) }
    var selectedRingtone by remember {
        mutableStateOf(
            ringtones.find { it.uri == config.ringtoneUri }
                ?: ringtones.firstOrNull()
                ?: RingtoneInfo("Default Alarm", "")
        )
    }
    var verifyCode by remember { mutableStateOf("") }
    var copyButtonText by remember { mutableStateOf("复制") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.monitor_alert),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.hms_push_status),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Use same color for all status texts
                val statusColor = if (pushToken != null)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.error
                // HMS Push Status
                Text(
                    text = if (pushToken != null) stringResource(R.string.hms_push_connected) else stringResource(R.string.hms_push_not_registered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor
                )
                // Activation Status (under HMS Push)
                Spacer(modifier = Modifier.height(8.dp))
                val context = LocalContext.current
                if (isActivating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.activation_status),
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp,
                            color = statusColor
                        )
                    }
                } else if (isActivated) {
                    // Show Tmall Genie address link when activated
                    deviceId?.let { id ->
                        val fpUrl = "https://fp.any-builder.com/?k=$id"
                        Column {
                            Text(
                                text = stringResource(R.string.tmall_genie_address),
                                style = MaterialTheme.typography.bodyMedium,
                                color = statusColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fpUrl,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).clickable {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://any-builder.com/find-the-phone/"))
                                        context.startActivity(intent)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = copyButtonText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier
                                        .background(
                                            color = androidx.compose.ui.graphics.Color(0xFF1976D2),
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = androidx.compose.ui.graphics.Color(0xFF1976D2),
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("URL", fpUrl)
                                            clipboard.setPrimaryClip(clip)
                                            copyButtonText = "已复制"
                                        }
                                )

                                LaunchedEffect(copyButtonText) {
                                    if (copyButtonText == "已复制") {
                                        kotlinx.coroutines.delay(1500)
                                        copyButtonText = "复制"
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.activation_status),
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor
                        )
                        Text(
                            text = stringResource(R.string.not_activated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor
                        )
                    }
                }
                // Manual activation section
                if (!isActivated) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = verifyCode,
                        onValueChange = { verifyCode = it },
                        label = { Text(stringResource(R.string.tmall_genie_verify_code)) },
                        placeholder = { Text(stringResource(R.string.enter_verify_code_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isActivating
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onActivate(verifyCode)
                            verifyCode = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isActivating && pushToken != null
                    ) {
                        if (isActivating) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).width(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.activation_button))
                        }
                    }
                }
                if (activationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activationError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.alert_settings),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        Text(stringResource(R.string.alert_volume, (volume * 100).toInt()))
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                onConfigChange(config.copy(volumeLevel = it))
            },
            valueRange = 0.05f..1.0f,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.vibrate_on_alert))
            Switch(
                checked = vibrateEnabled,
                onCheckedChange = {
                    vibrateEnabled = it
                    onConfigChange(config.copy(vibrateEnabled = it))
                }
            )
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedRingtone.name,
                onValueChange = {},
                label = { Text(stringResource(R.string.alert_ringtone)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ringtoneExpanded = true },
                readOnly = true,
                enabled = false
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clickable { ringtoneExpanded = true },
                color = androidx.compose.ui.graphics.Color.Transparent
            ) {}

            DropdownMenu(
                expanded = ringtoneExpanded,
                onDismissRequest = { ringtoneExpanded = false }
            ) {
                ringtones.forEach { ringtone ->
                    DropdownMenuItem(
                        text = { Text(ringtone.name) },
                        onClick = {
                            selectedRingtone = ringtone
                            onConfigChange(config.copy(
                                ringtoneUri = ringtone.uri,
                                ringtoneName = ringtone.name
                            ))
                            onPreviewRingtone(ringtone.uri, config.volumeLevel)
                            ringtoneExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
