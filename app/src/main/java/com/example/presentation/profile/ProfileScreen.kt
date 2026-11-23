package com.example.presentation.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.presentation.components.UserAvatar
import com.example.presentation.schedule.ScheduleUiState
import com.example.ui.theme.ActiveStatusEmerald
import com.example.ui.theme.HeroNavyGradient
import com.example.ui.theme.InactiveStatusSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.P1Crimson
import com.example.ui.theme.PingDuluNavy
import com.example.ui.theme.PingDuluTheme
import com.example.ui.theme.RoleAdminRoseBgDark
import com.example.ui.theme.RoleAdminRoseBgLight
import com.example.ui.theme.RoleAdminRoseBorderLight
import com.example.ui.theme.RoleAdminRoseFgDark
import com.example.ui.theme.RoleAdminRoseFgLight
import com.example.ui.theme.RoleTechCobaltBgDark
import com.example.ui.theme.RoleTechCobaltBgLight
import com.example.ui.theme.RoleTechCobaltBorderLight
import com.example.ui.theme.RoleTechCobaltFgDark
import com.example.ui.theme.RoleTechCobaltFgLight

@Composable
fun ProfileScreen(
    uiState: ScheduleUiState,
    onBackClick: () -> Unit,
    onRefreshProfile: () -> Unit,
    onPickAvatarUri: (Uri) -> Unit,
    onDeleteAvatar: () -> Unit,
    onUpdateProfile: (displayName: String?, newPassword: String?, onSuccess: () -> Unit) -> Unit,
    onClearProfileError: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)

    var editableDisplayName by rememberSaveable(uiState.technicianName) {
        mutableStateOf(uiState.technicianName)
    }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var localValidationError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onRefreshProfile()
    }

    // Play-Policy Compliant Zero-Permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickAvatarUri(uri)
        }
    }

    val isDark = uiState.isDarkTheme
    val extendedColors = PingDuluTheme.extendedColors
    val isAdmin = uiState.technicianRole.trim().equals("admin", ignoreCase = true)
    val isViewer = uiState.technicianRole.trim().equals("viewer", ignoreCase = true)
    val roleLabel = when {
        isAdmin -> "Administrator"
        isViewer -> "Viewer"
        else -> "Teknisi Lapangan"
    }
    val roleBg = when {
        isAdmin -> if (isDark) RoleAdminRoseBgDark else RoleAdminRoseBgLight
        else -> if (isDark) RoleTechCobaltBgDark else RoleTechCobaltBgLight
    }
    val roleFg = when {
        isAdmin -> if (isDark) RoleAdminRoseFgDark else RoleAdminRoseFgLight
        else -> if (isDark) RoleTechCobaltFgDark else RoleTechCobaltFgLight
    }
    val roleBorder = when {
        isAdmin -> if (isDark) RoleAdminRoseFgDark.copy(alpha = 0.35f) else RoleAdminRoseBorderLight
        else -> if (isDark) RoleTechCobaltFgDark.copy(alpha = 0.35f) else RoleTechCobaltBorderLight
    }
    val hasAvatar = !uiState.technicianAvatarUrl.isNullOrBlank()
    val usernameText = uiState.technicianUsername.trim().ifBlank {
        uiState.technicianName.trim().lowercase().replace(" ", ".")
    }
    val combinedError = localValidationError ?: uiState.profileErrorMessage

    val hasNameChanged = editableDisplayName.trim().isNotEmpty() &&
        editableDisplayName.trim() != uiState.technicianName.trim()
    val hasPasswordInput = newPassword.isNotEmpty() || confirmPassword.isNotEmpty()
    val canSubmitChanges = !uiState.isUpdatingProfile && (hasNameChanged || hasPasswordInput)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .testTag("profile_screen")
    ) {
        // Top App Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali ke layar utama"
                        )
                    }
                    Column {
                        Text(
                            text = "Profil Teknisi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Foto profil, departemen & keamanan akun",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshProfile,
                    modifier = Modifier.testTag("profile_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Muat ulang profil dari server",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Hero Avatar & Identity Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_header_card"),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Brand Gradient Banner Top Strip
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HeroNavyGradient)
                                .padding(top = 24.dp, bottom = 22.dp, start = 16.dp, end = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    UserAvatar(
                                        avatarUrl = uiState.technicianAvatarUrl,
                                        displayName = uiState.technicianName,
                                        size = 108.dp,
                                        isActive = uiState.technicianIsActive,
                                        showActiveBadge = true,
                                        serverBaseUrl = uiState.serverBaseUrl,
                                        ringColor = Color.White,
                                        ringWidth = 3.dp,
                                        modifier = Modifier.testTag("profile_large_avatar")
                                    )
                                    if (uiState.isUploadingAvatar) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(30.dp),
                                                strokeWidth = 3.dp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = {
                                            if (!uiState.isUploadingAvatar) {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(
                                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                                    )
                                                )
                                            }
                                        },
                                        shape = CircleShape,
                                        color = PingDuluNavy,
                                        border = BorderStroke(2.dp, Color.White),
                                        modifier = Modifier
                                            .size(38.dp)
                                            .testTag("profile_camera_overlay_button")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.CameraAlt,
                                                contentDescription = "Pilih foto profil",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                        },
                                        enabled = !uiState.isUploadingAvatar,
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("profile_upload_avatar_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Upload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (uiState.isUploadingAvatar) "Mengunggah..." else "Unggah Foto",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    if (hasAvatar) {
                                        TextButton(
                                            onClick = onDeleteAvatar,
                                            enabled = !uiState.isUploadingAvatar,
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = RoleAdminRoseFgDark
                                            ),
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 48.dp)
                                                .testTag("profile_delete_avatar_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Hapus Foto",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "Maksimal 10 MB (JPEG, PNG, WebP)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.testTag("profile_upload_limit_caption")
                                )
                            }
                        }

                        // Name, @username, Active Pill & Role Badge
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.technicianName.ifBlank { "Teknisi" },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("profile_display_name")
                            )
                            Text(
                                text = "@$usernameText",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("profile_username")
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Active / Inactive Status Pill
                                val statusColor = if (uiState.technicianIsActive) {
                                    ActiveStatusEmerald
                                } else {
                                    InactiveStatusSlate
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = statusColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("profile_active_status_pill")
                                ) {
                                    Text(
                                        text = if (uiState.technicianIsActive) "● Aktif" else "○ Nonaktif",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }

                                // Role Badge
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = roleBg,
                                    border = BorderStroke(1.dp, roleBorder),
                                    modifier = Modifier
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("profile_role_badge")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Badge,
                                            contentDescription = null,
                                            tint = roleFg,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = roleLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = roleFg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Department & Sub-Department Info Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_department_card"),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DEPARTEMEN & BAGIAN",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.6.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PingDuluNavy.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Apartment,
                                    contentDescription = null,
                                    tint = PingDuluNavy,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Departemen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.technicianDepartment?.takeIf { it.isNotBlank() }
                                        ?: "Belum diatur oleh Admin",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("profile_department_value")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ActiveStatusEmerald.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Groups,
                                    contentDescription = null,
                                    tint = ActiveStatusEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Bagian / Sub-Departemen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = uiState.technicianSubDepartment?.takeIf { it.isNotBlank() }
                                        ?: "Belum diatur oleh Admin",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("profile_sub_department_value")
                                )
                            }
                        }
                    }
                }

                // 3. Self-Service Name & Password Change Form
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_password_form_card"),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Ubah Profil & Kata Sandi Mandiri",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Kosongkan kolom kata sandi jika hanya ingin memperbarui nama tampilan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!combinedError.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics(mergeDescendants = true) {}
                                    .testTag("profile_error_banner"),
                                shape = RoundedCornerShape(12.dp),
                                color = extendedColors.priorityP1.container,
                                border = BorderStroke(1.dp, extendedColors.priorityP1.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = null,
                                        tint = P1Crimson,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = combinedError,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = extendedColors.priorityP1.content
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editableDisplayName,
                            onValueChange = {
                                editableDisplayName = it
                                localValidationError = null
                                if (!uiState.profileErrorMessage.isNullOrBlank()) onClearProfileError()
                            },
                            label = { Text("Nama Lengkap") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Person, contentDescription = null)
                            },
                            singleLine = true,
                            enabled = !uiState.isUpdatingProfile,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_edit_name_input")
                        )

                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                localValidationError = null
                                if (!uiState.profileErrorMessage.isNullOrBlank()) onClearProfileError()
                            },
                            label = { Text("Kata Sandi Baru") },
                            placeholder = { Text("Minimal 4 karakter") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { passwordVisible = !passwordVisible },
                                    modifier = Modifier.testTag("profile_password_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Rounded.VisibilityOff
                                        } else {
                                            Icons.Rounded.Visibility
                                        },
                                        contentDescription = if (passwordVisible) {
                                            "Sembunyikan kata sandi baru"
                                        } else {
                                            "Tampilkan kata sandi baru"
                                        }
                                    )
                                }
                            },
                            singleLine = true,
                            enabled = !uiState.isUpdatingProfile,
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_new_password_input")
                        )

                        if (newPassword.isNotEmpty()) {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = {
                                    confirmPassword = it
                                    localValidationError = null
                                },
                                label = { Text("Konfirmasi Kata Sandi Baru") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
                                },
                                singleLine = true,
                                enabled = !uiState.isUpdatingProfile,
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_confirm_password_input")
                            )
                        }

                        Button(
                            onClick = {
                                val trimmedName = editableDisplayName.trim()
                                if (trimmedName.isEmpty()) {
                                    localValidationError = "Nama lengkap tidak boleh kosong."
                                    return@Button
                                }
                                if (newPassword.isNotEmpty()) {
                                    if (newPassword.length < 4) {
                                        localValidationError = "Kata sandi baru minimal 4 karakter."
                                        return@Button
                                    }
                                    if (newPassword != confirmPassword) {
                                        localValidationError = "Konfirmasi kata sandi tidak cocok."
                                        return@Button
                                    }
                                }
                                localValidationError = null
                                onUpdateProfile(
                                    trimmedName.takeIf { hasNameChanged },
                                    newPassword.takeIf { it.isNotEmpty() }
                                ) {
                                    newPassword = ""
                                    confirmPassword = ""
                                }
                            },
                            enabled = canSubmitChanges,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 50.dp)
                                .testTag("profile_save_changes_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PingDuluNavy,
                                contentColor = Color.White
                            )
                        ) {
                            if (uiState.isUpdatingProfile) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Menyimpan Perubahan...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
