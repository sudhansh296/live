package com.coderlobby.hivo.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderlobby.hivo.R
import com.coderlobby.hivo.auth.AuthError
import com.coderlobby.hivo.ui.theme.HivoRed

private val ErrorRed = Color(0xFFFF6B6B)
private val DisabledContainer = Color(0xFF3A1414)
private val DisabledContent = Color(0xFF8A6A6A)

@Composable
fun HivoPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Button(
        onClick = { if (!busy) onClick() },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = HivoRed,
            contentColor = Color.White,
            disabledContainerColor = DisabledContainer,
            disabledContentColor = DisabledContent,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Placeholder look. The final button uses Google's official brand assets. */
@Composable
fun GoogleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Button(
        onClick = { if (!busy) onClick() },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color(0xFFE6E6E6),
            disabledContentColor = Color(0xFF8A8A8A),
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF4285F4), strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("G", color = Color(0xFF4285F4), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun HivoLogoTile(size: Int = 84) {
    Box(
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.26f).dp)).background(HivoRed),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_hivo_h),
            contentDescription = null,
            modifier = Modifier.size((size * 0.55f).dp),
        )
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(top = 8.dp)) {
        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back), tint = Color.White)
    }
}

@StringRes
fun AuthError.messageRes(): Int = when (this) {
    AuthError.Network -> R.string.err_network
    AuthError.InvalidPhone -> R.string.err_invalid_phone
    AuthError.TooMany -> R.string.err_too_many
    AuthError.NotConfigured -> R.string.err_not_configured
    AuthError.NoGoogleAccount -> R.string.err_no_google_account
    AuthError.CodeExpired -> R.string.err_code_expired
    AuthError.Suspended -> R.string.err_suspended
    AuthError.Server -> R.string.err_server
    AuthError.Generic -> R.string.err_generic
}

@Composable
fun ErrorText(error: AuthError?, modifier: Modifier = Modifier) {
    if (error != null) {
        Text(stringResource(error.messageRes()), color = ErrorRed, fontSize = 13.sp, modifier = modifier.padding(bottom = 10.dp))
    }
}

@Composable
fun ErrorMessage(text: String, modifier: Modifier = Modifier) {
    Text(text, color = ErrorRed, fontSize = 13.sp, modifier = modifier.fillMaxWidth().padding(bottom = 10.dp))
}
