package com.coderlobby.hivo.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderlobby.hivo.Config
import com.coderlobby.hivo.R
import com.coderlobby.hivo.auth.AuthUi
import com.coderlobby.hivo.ui.theme.HivoCard
import com.coderlobby.hivo.ui.theme.HivoRed

private val WrongRed = Color(0xFFFF5252)

@Composable
fun OtpScreen(
    ui: AuthUi,
    onBack: () -> Unit,
    onOtpChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Submit by itself as soon as the 6th digit is typed (or pasted by SMS auto-fill).
    LaunchedEffect(ui.otp, ui.otpWrong) {
        if (ui.otp.length == Config.OTP_LENGTH && !ui.otpWrong) onVerify()
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 22.dp)) {
        BackButton(onBack)
        Text(
            stringResource(R.string.otp_title),
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        )
        Row(modifier = Modifier.padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.otp_sent_to, ui.phoneDisplay),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.5.sp,
            )
            Text(
                stringResource(R.string.change),
                color = Color.White,
                fontSize = 13.5.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onBack),
            )
        }

        // One hidden text field receives the digits; the six boxes only draw them.
        BasicTextField(
            value = ui.otp,
            onValueChange = onOtpChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            decorationBox = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(Config.OTP_LENGTH) { index ->
                        OtpBox(
                            digit = ui.otp.getOrNull(index)?.toString().orEmpty(),
                            active = index == ui.otp.length && !ui.otpWrong,
                            wrong = ui.otpWrong,
                        )
                    }
                }
            },
        )

        Spacer(Modifier.height(14.dp))
        when {
            ui.otpWrong && ui.otpAttemptsLeft > 0 ->
                ErrorMessage(stringResource(R.string.otp_wrong, ui.otpAttemptsLeft))
            ui.otpWrong -> ErrorMessage(stringResource(R.string.otp_locked))
            else -> ErrorText(ui.error, Modifier.fillMaxWidth())
        }

        if (ui.resendSeconds > 0) {
            val time = "0:%02d".format(ui.resendSeconds)
            Text(stringResource(R.string.resend_in, time), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } else {
            Text(
                stringResource(R.string.resend),
                color = Color.White,
                fontSize = 13.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onResend),
            )
        }

        Spacer(Modifier.weight(1f))

        HivoPrimaryButton(
            text = stringResource(R.string.verify),
            onClick = onVerify,
            enabled = ui.otp.length == Config.OTP_LENGTH && ui.otpAttemptsLeft > 0,
            busy = ui.busy,
        )
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun OtpBox(digit: String, active: Boolean, wrong: Boolean) {
    val borderColor = when {
        wrong -> WrongRed
        active -> HivoRed
        else -> Color(0xFF2C2C2C)
    }
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 54.dp)
            .background(HivoCard, RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(digit, color = if (wrong) Color(0xFFFF8A8A) else Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
