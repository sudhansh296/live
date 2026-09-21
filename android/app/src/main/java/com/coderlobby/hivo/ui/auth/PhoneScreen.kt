package com.coderlobby.hivo.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.coderlobby.hivo.R
import com.coderlobby.hivo.auth.AuthUi
import com.coderlobby.hivo.auth.Country
import com.coderlobby.hivo.ui.theme.HivoCard
import com.coderlobby.hivo.ui.theme.HivoRed

@Composable
fun PhoneScreen(
    ui: AuthUi,
    countries: List<Country>,
    onBack: () -> Unit,
    onPhoneChange: (String) -> Unit,
    onCountryPick: (Country) -> Unit,
    onSend: () -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 22.dp)) {
        BackButton(onBack)
        Text(
            stringResource(R.string.phone_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        )
        Text(
            stringResource(R.string.phone_lead),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            modifier = Modifier.padding(bottom = 22.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .background(HivoCard, RoundedCornerShape(12.dp))
                    .clickable { pickerOpen = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(ui.country.flag, fontSize = 20.sp)
                Text("+${ui.country.dialCode}", color = Color.White, fontSize = 15.sp)
                Text("▾", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            }
            OutlinedTextField(
                value = ui.phoneInput,
                onValueChange = onPhoneChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp, color = Color.White),
                placeholder = { Text(stringResource(R.string.phone_hint), color = Color(0xFF666666)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ui.phoneInput.length >= MIN_DIGITS) onSend() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HivoRed,
                    unfocusedBorderColor = Color(0xFF2C2C2C),
                    focusedContainerColor = HivoCard,
                    unfocusedContainerColor = HivoCard,
                    cursorColor = Color.White,
                ),
            )
        }
        Text(
            stringResource(R.string.phone_help),
            color = Color(0xFF777777),
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.weight(1f))

        ErrorText(ui.error, Modifier.fillMaxWidth())
        HivoPrimaryButton(
            text = stringResource(R.string.send_code),
            onClick = onSend,
            enabled = ui.phoneInput.length >= MIN_DIGITS,
            busy = ui.busy,
        )
        Spacer(Modifier.height(26.dp))
    }

    if (pickerOpen) {
        CountryPickerDialog(
            countries = countries,
            onPick = {
                onCountryPick(it)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

// Cheap check to enable the button; the real validation is libphonenumber's when "Send code" is pressed.
private const val MIN_DIGITS = 6

@Composable
internal fun CountryPickerDialog(
    countries: List<Country>,
    onPick: (Country) -> Unit,
    onDismiss: () -> Unit,
    showDialCode: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(query, countries, showDialCode) {
        val q = query.trim().removePrefix("+")
        if (q.isEmpty()) {
            countries
        } else {
            countries.filter { it.name.contains(q, ignoreCase = true) || (showDialCode && it.dialCode.toString().startsWith(q)) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.heightIn(max = 520.dp).padding(16.dp)) {
                Text(stringResource(R.string.choose_country), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_country), color = Color(0xFF666666)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HivoRed,
                        unfocusedBorderColor = Color(0xFF2C2C2C),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White,
                    ),
                )
                LazyColumn {
                    items(shown, key = { it.region }) { country ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(country) }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(country.flag, fontSize = 20.sp)
                            Text(country.name, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (showDialCode) Text("+${country.dialCode}", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
