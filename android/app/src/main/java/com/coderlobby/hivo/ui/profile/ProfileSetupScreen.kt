package com.coderlobby.hivo.ui.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coderlobby.hivo.R
import com.coderlobby.hivo.profile.ProfileError
import com.coderlobby.hivo.profile.ProfileRules
import com.coderlobby.hivo.profile.ProfileSetupState
import com.coderlobby.hivo.profile.ProfileSetupViewModel
import com.coderlobby.hivo.profile.UsernameStatus
import com.coderlobby.hivo.ui.auth.CountryPickerDialog
import com.coderlobby.hivo.ui.auth.ErrorMessage
import com.coderlobby.hivo.ui.auth.HivoPrimaryButton
import com.coderlobby.hivo.ui.theme.HivoCard
import com.coderlobby.hivo.ui.theme.HivoCardAlt
import com.coderlobby.hivo.ui.theme.HivoGreen
import com.coderlobby.hivo.ui.theme.HivoRed
import java.io.File
import java.util.Calendar
import java.util.TimeZone

private val Bad = Color(0xFFFF6B6B)
private val Muted = Color(0xFF888888)
private val IdleBorder = Color(0xFF2C2C2C)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(vm: ProfileSetupViewModel, onSignOut: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()

    // The server said no because of the birth date: nothing more to fill in on this account.
    if (state.ageBlocked) {
        AgeBlockedScreen(onSignOut)
        return
    }

    // A photo was just chosen: crop it first (its own full screen), then it is uploaded.
    val cropSource = state.cropSource
    if (cropSource != null) {
        AvatarCropScreen(cropSource, onCancel = vm::onCropCancelled, onDone = vm::onCropConfirmed)
        return
    }

    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val imeVisible = WindowInsets.isImeVisible
    var photoMenuOpen by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var countryPickerOpen by remember { mutableStateOf(false) }

    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) cameraUri?.let { vm.onPhotoChosen(context, it) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onPhotoChosen(context, uri)
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 22.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.profile_title),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 28.dp, bottom = 6.dp),
            )
            Text(
                stringResource(R.string.profile_lead),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
            )

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                AvatarPicker(
                    avatar = state.avatar,
                    initial = initialOf(state.displayName),
                    busy = state.avatarBusy,
                    onClick = { photoMenuOpen = true },
                )
            }

            FieldLabel(stringResource(R.string.profile_name))
            OutlinedTextField(
                value = state.displayName,
                onValueChange = vm::onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.profile_name_hint), color = Color(0xFF666666)) },
                trailingIcon = {
                    Text(
                        "${ProfileRules.displayNameLength(state.displayName)}/${ProfileRules.NAME_MAX}",
                        color = Color(0xFF777777),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(end = 14.dp),
                    )
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
            )

            FieldLabel(stringResource(R.string.profile_username))
            UsernameField(state, vm::onUsernameChanged, onDone = { focus.clearFocus() })
            UsernameMessage(state)

            FieldLabel(stringResource(R.string.profile_birth))
            PickerField(onClick = { focus.clearFocus(); datePickerOpen = true }) {
                val millis = state.birthDateMillis
                Text(
                    if (millis == null) stringResource(R.string.profile_birth_hint) else ProfileRules.shownDate(millis),
                    color = if (millis == null) Color(0xFF666666) else Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_calendar), contentDescription = null, tint = Muted, modifier = Modifier.size(20.dp))
            }
            Text(
                stringResource(R.string.profile_birth_note),
                color = Muted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            FieldLabel(stringResource(R.string.profile_country))
            PickerField(onClick = { focus.clearFocus(); countryPickerOpen = true }) {
                Text(state.country.flag, fontSize = 20.sp)
                Text(state.country.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text("▾", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        // While the keyboard is open the screen is short: the fields get the room, the buttons come back when it closes.
        if (!imeVisible) {
            state.error?.let { ErrorMessage(stringResource(it.messageRes()), Modifier.padding(top = 8.dp)) }
            HivoPrimaryButton(
                text = stringResource(R.string.profile_continue),
                onClick = vm::submit,
                enabled = state.canSubmit,
                busy = state.submitting,
            )
            TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.sign_out), color = Muted, fontSize = 13.sp)
            }
        }
    }

    if (photoMenuOpen) {
        PhotoMenu(
            canRemove = state.avatar != null,
            onDismiss = { photoMenuOpen = false },
            onTake = {
                photoMenuOpen = false
                val uri = newCameraUri(context)
                cameraUri = uri
                try {
                    camera.launch(uri)
                } catch (_: ActivityNotFoundException) {
                    vm.onCameraMissing()
                }
            },
            onChoose = {
                photoMenuOpen = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemove = {
                photoMenuOpen = false
                vm.onPhotoRemoved()
            },
        )
    }
    if (datePickerOpen) {
        BirthDatePicker(
            initial = state.birthDateMillis,
            onPick = {
                vm.onBirthDatePicked(it)
                datePickerOpen = false
            },
            onDismiss = { datePickerOpen = false },
        )
    }
    if (countryPickerOpen) {
        CountryPickerDialog(
            countries = vm.countries,
            showDialCode = false,
            onPick = {
                vm.onCountryPicked(it)
                countryPickerOpen = false
            },
            onDismiss = { countryPickerOpen = false },
        )
    }
}

// ── Pieces ────────────────────────────────────────────────────────────────────

@Composable
private fun AvatarPicker(avatar: Bitmap?, initial: String, busy: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(104.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(96.dp)
                .clip(CircleShape)
                .background(HivoCardAlt)
                .border(1.dp, IdleBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar != null) {
                Image(avatar.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(initial, color = Color(0xFF777777), fontSize = 38.sp, fontWeight = FontWeight.Bold)
            }
            if (busy) {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(32.dp)
                .clip(CircleShape)
                .background(HivoRed)
                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_camera),
                contentDescription = stringResource(R.string.profile_photo_change),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Muted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 14.dp, bottom = 5.dp))
}

@Composable
private fun fieldColors(idleBorder: Color = IdleBorder) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HivoRed,
    unfocusedBorderColor = idleBorder,
    errorBorderColor = Bad,
    focusedContainerColor = HivoCard,
    unfocusedContainerColor = HivoCard,
    errorContainerColor = HivoCard,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    errorTextColor = Color.White,
    cursorColor = Color.White,
    errorCursorColor = Color.White,
)

@Composable
private fun UsernameField(state: ProfileSetupState, onChange: (String) -> Unit, onDone: () -> Unit) {
    val status = state.usernameStatus
    val isBad = status == UsernameStatus.BadShape || status == UsernameStatus.Taken || status == UsernameStatus.Reserved
    OutlinedTextField(
        value = state.username,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isBad,
        prefix = { Text("@", color = Muted, fontSize = 16.sp) },
        placeholder = { Text(stringResource(R.string.profile_username_hint), color = Color(0xFF666666)) },
        trailingIcon = {
            Box(modifier = Modifier.padding(end = 14.dp)) {
                when {
                    status == UsernameStatus.Checking ->
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Muted, strokeWidth = 2.dp)
                    status == UsernameStatus.Available -> Text("✓", color = HivoGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    isBad -> Text("✕", color = Bad, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(idleBorder = if (status == UsernameStatus.Available) HivoGreen else IdleBorder),
    )
}

@Composable
private fun UsernameMessage(state: ProfileSetupState) {
    val name = state.username
    val (text, color) = when (state.usernameStatus) {
        UsernameStatus.Empty, UsernameStatus.TooShort -> stringResource(R.string.username_rules) to Muted
        UsernameStatus.BadShape -> stringResource(R.string.username_bad_shape) to Bad
        UsernameStatus.Checking -> stringResource(R.string.username_checking) to Muted
        UsernameStatus.Available -> stringResource(R.string.username_available, name) to HivoGreen
        UsernameStatus.Taken -> stringResource(R.string.username_taken, name) to Bad
        UsernameStatus.Reserved -> stringResource(R.string.username_reserved) to Bad
        UsernameStatus.Unreachable -> stringResource(R.string.username_unreachable) to Muted
    }
    Text(text, color = color, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
}

/** A tappable box that looks like the text fields (date of birth, country). */
@Composable
private fun PickerField(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HivoCard)
            .border(1.dp, IdleBorder, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PhotoMenu(
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onTake: () -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                MenuRow(stringResource(R.string.photo_take), Color.White, onTake)
                MenuRow(stringResource(R.string.photo_choose), Color.White, onChoose)
                if (canRemove) MenuRow(stringResource(R.string.photo_remove), Bad, onRemove)
            }
        }
    }
}

@Composable
private fun MenuRow(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 16.sp,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 22.dp, vertical = 16.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthDatePicker(initial: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    val thisYear = remember { Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.YEAR) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        // Start around the age most people are, so nobody scrolls back through decades.
        initialDisplayedMonthMillis = initial ?: (now - 25L * 365 * 24 * 60 * 60 * 1000),
        yearRange = 1900..thisYear,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= now
            override fun isSelectableYear(year: Int) = year <= thisYear
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = { state.selectedDateMillis?.let(onPick) }) {
                Text(stringResource(R.string.ok), color = HivoRed)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Muted) } },
    ) {
        DatePicker(state = state)
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun initialOf(displayName: String): String {
    val cleaned = ProfileRules.cleanDisplayName(displayName)
    if (cleaned.isEmpty()) return "?"
    return String(Character.toChars(cleaned.codePointAt(0))).uppercase()
}

// A fresh file inside cache/camera (the only folder the FileProvider shares). Old shots are removed first.
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@StringRes
private fun ProfileError.messageRes(): Int = when (this) {
    ProfileError.Network -> R.string.err_network
    ProfileError.Server -> R.string.err_server
    ProfileError.Generic -> R.string.err_generic
    ProfileError.TooMany -> R.string.err_too_many
    ProfileError.DisplayName -> R.string.err_display_name
    ProfileError.BirthDate -> R.string.err_birth_date
    ProfileError.Country -> R.string.err_country
    ProfileError.PhotoTooLarge -> R.string.err_photo_too_large
    ProfileError.PhotoInvalid -> R.string.err_photo_invalid
    ProfileError.PhotoUnsupported -> R.string.err_photo_unsupported
    ProfileError.NoCamera -> R.string.err_no_camera
}
