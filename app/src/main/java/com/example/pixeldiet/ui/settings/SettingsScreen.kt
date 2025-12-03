package com.example.pixeldiet.ui.settings

import android.app.Activity
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.pixeldiet.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
// ⭐️ LocalContext import는 이제 필요 없습니다.
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pixeldiet.backup.LoginActivity

import com.example.pixeldiet.model.NotificationSettings
import com.example.pixeldiet.viewmodel.SharedViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun SettingsScreen(viewModel: SharedViewModel = viewModel()) {

    val context = LocalContext.current
    val activity = context as? Activity

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken != null) {
                    viewModel.onGoogleLoginSuccess(idToken)
                    Toast.makeText(context, "구글 로그인 완료", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e("GoogleLogin", "Login failed: ${e.statusCode}")
            }
        }
    }

    val isGoogleUser by viewModel.isGoogleUser.collectAsState()

    var showIndividualSettings by remember { mutableStateOf(false) }
    var showTotalSettings by remember { mutableStateOf(false) }
    val settings by viewModel.notificationSettings.observeAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("알림 설정", fontSize = 20.sp, fontWeight = FontWeight.Bold) }

        // 알람 버튼들
        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                Column {
                    SettingsItem(
                        title = "개별 앱 시간 알람",
                        icon = Icons.Default.Notifications,
                        iconTint = Color(0xFFE1306C),
                        onClick = { showIndividualSettings = true }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        title = "전체시간 알람",
                        icon = Icons.Default.Notifications,
                        iconTint = Color(0xFFFFC107),
                        onClick = { showTotalSettings = true }
                    )
                }
            }
        }

        // 구글 로그인/로그아웃 버튼
        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                SettingsItem(
                    title = if (isGoogleUser) "로그아웃" else "계정 연동하기",
                    icon = Icons.Default.AccountCircle,
                    iconTint = Color(0xFF4285F4),
                    onClick = {
                        if (activity == null) return@SettingsItem

                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(context.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        val googleSignInClient = GoogleSignIn.getClient(activity, gso)

                        if (isGoogleUser) {
                            viewModel.logout()
                            googleSignInClient.signOut().addOnCompleteListener {
                                val intent = Intent(activity, LoginActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                activity.startActivity(intent)
                            }
                        } else {
                            googleSignInClient.revokeAccess().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        }
                    }
                )
            }
        }
    }

    if (showIndividualSettings) {
        NotificationSettingDialog(
            title = "개별 앱 시간 알람",
            // ⭐️ [수정] settings가 null일 경우 기본값만 전달 (context 주입 삭제)
            currentSettings = settings ?: NotificationSettings(),
            getCheckedItems = { s -> booleanArrayOf(s.individualApp50, s.individualApp70, s.individualApp100) },
            onDismiss = { showIndividualSettings = false },
            onSave = { newSettings -> viewModel.saveNotificationSettings(newSettings); showIndividualSettings = false },
            onShowExample = { /* TODO */ },
            updateLogic = { current, index, isChecked ->
                when (index) {
                    0 -> current.individualApp50 = isChecked
                    1 -> current.individualApp70 = isChecked
                    2 -> current.individualApp100 = isChecked
                }
            },
            onIntervalSelected = { newSettings, interval ->
                newSettings.repeatIntervalMinutes = interval
            }
        )
    }

    if (showTotalSettings) {
        NotificationSettingDialog(
            title = "전체 시간 알람",
            // ⭐️ [수정] settings가 null일 경우 기본값만 전달 (context 주입 삭제)
            currentSettings = settings ?: NotificationSettings(),
            getCheckedItems = { s -> booleanArrayOf(s.total50, s.total70, s.total100) },
            onDismiss = { showTotalSettings = false },
            onSave = { newSettings -> viewModel.saveNotificationSettings(newSettings); showTotalSettings = false },
            onShowExample = { /* TODO */ },
            updateLogic = { current, index, isChecked ->
                when (index) {
                    0 -> current.total50 = isChecked
                    1 -> current.total70 = isChecked
                    2 -> current.total100 = isChecked
                }
            },
            onIntervalSelected = { newSettings, interval ->
                newSettings.repeatIntervalMinutes = interval
            }
        )
    }

    // ... (이하 SettingsItem, NotificationSettingDialog 코드는 이전과 동일) ...
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, iconTint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = iconTint)
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingDialog(
    title: String,
    currentSettings: NotificationSettings,
    getCheckedItems: (NotificationSettings) -> BooleanArray,
    onDismiss: () -> Unit,
    onSave: (NotificationSettings) -> Unit,
    onShowExample: () -> Unit,
    updateLogic: (NotificationSettings, Int, Boolean) -> Unit,
    onIntervalSelected: (NotificationSettings, Int) -> Unit
) {
    val items = listOf("50% 도달 알림", "70% 도달 알림", "100% 초과 반복 알림")
    val tempSettings = remember { mutableStateOf(currentSettings.copy()) }
    val checkedStates = remember {
        mutableStateListOf(*getCheckedItems(tempSettings.value).toTypedArray())
    }
    val intervalOptions = listOf(3, 5, 10, 15, 30)
    var intervalExpanded by remember { mutableStateOf(false) }
    var selectedInterval by remember { mutableStateOf(tempSettings.value.repeatIntervalMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                items.take(2).forEachIndexed { index, text ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checkedStates[index],
                            onCheckedChange = { isChecked ->
                                checkedStates[index] = isChecked
                                updateLogic(tempSettings.value, index, isChecked)
                            }
                        )
                        Text(text)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checkedStates[2],
                        onCheckedChange = { isChecked ->
                            checkedStates[2] = isChecked
                            updateLogic(tempSettings.value, 2, isChecked)
                        }
                    )
                    Text(items[2])
                }
                if (checkedStates[2]) {
                    ExposedDropdownMenuBox(
                        expanded = intervalExpanded,
                        onExpandedChange = { intervalExpanded = !intervalExpanded },
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = "$selectedInterval 분마다",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = intervalExpanded,
                            onDismissRequest = { intervalExpanded = false }
                        ) {
                            intervalOptions.forEach { interval ->
                                DropdownMenuItem(
                                    text = { Text("$interval 분마다") },
                                    onClick = {
                                        selectedInterval = interval
                                        onIntervalSelected(tempSettings.value, interval)
                                        intervalExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onShowExample) {
                    Text("알림 예시 보기")
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(tempSettings.value) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}