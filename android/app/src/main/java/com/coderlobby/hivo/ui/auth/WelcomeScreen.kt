package com.coderlobby.hivo.ui.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderlobby.hivo.R
import com.coderlobby.hivo.auth.AuthError
import com.coderlobby.hivo.ui.theme.HivoRed

private val WarnRed = Color(0xFFFF6B6B)

@Composable
fun WelcomeScreen(
    busy: Boolean,
    error: AuthError?,
    termsAccepted: Boolean,
    termsHintCount: Int,
    onTermsChange: (Boolean) -> Unit,
    onTermsRequired: () -> Unit,
    onGoogle: () -> Unit,
    onPhone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(34.dp))
        HivoLogoTile()
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.app_name),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            stringResource(R.string.tagline),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(22.dp))
        HeroCards()

        Spacer(Modifier.weight(1f))

        ErrorText(error, Modifier.fillMaxWidth())
        // The buttons always look normal. Without the tick they do not sign in: they shake the checkbox instead.
        // (The server also refuses a login that did not come with the confirmation.)
        GoogleButton(
            text = stringResource(R.string.continue_google),
            onClick = { if (termsAccepted) onGoogle() else onTermsRequired() },
            busy = busy,
        )
        Spacer(Modifier.height(10.dp))
        // While Google sign-in runs, the spinner belongs to the Google button; this one only waits.
        HivoPrimaryButton(
            text = stringResource(R.string.continue_phone),
            onClick = { if (termsAccepted) onPhone() else onTermsRequired() },
            enabled = !busy,
        )
        Spacer(Modifier.height(10.dp))
        TermsCheckbox(checked = termsAccepted, hintCount = termsHintCount, onCheckedChange = onTermsChange)
        Spacer(Modifier.height(18.dp))
    }
}

/** One mandatory checkbox for "18 or older" and Terms/Privacy. Never pre-ticked. */
@Composable
private fun TermsCheckbox(checked: Boolean, hintCount: Int, onCheckedChange: (Boolean) -> Unit) {
    val warn = hintCount > 0 && !checked

    // A short left-right shake each time a sign-in button is tapped without the tick.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(hintCount) {
        if (hintCount > 0) {
            repeat(3) {
                shake.animateTo(8f, tween(45))
                shake.animateTo(-8f, tween(45))
            }
            shake.animateTo(0f, tween(45))
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = shake.value.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null, // the whole row is the touch target
                colors = CheckboxDefaults.colors(
                    checkedColor = HivoRed,
                    uncheckedColor = if (warn) WarnRed else Color(0xFF8A8A8A),
                    checkmarkColor = Color.White,
                ),
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            )
            Text(
                stringResource(R.string.terms_checkbox),
                color = if (warn) WarnRed else Color(0xFFB0B0B0),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (warn) {
            Text(
                stringResource(R.string.terms_required),
                color = WarnRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 48.dp, top = 2.dp),
            )
        }
    }
}

// Decorative only: no server data. Three tilted "live room" cards.
@Composable
private fun HeroCards() {
    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.TopCenter) {
        RoomCard(
            colors = listOf(Color(0xFF6D1A1A), Color(0xFF241010)),
            viewers = "1.2k",
            modifier = Modifier.offset(x = (-70).dp, y = 14.dp).rotate(-8f),
        )
        RoomCard(
            colors = listOf(Color(0xFF4A1A3A), Color(0xFF170D14)),
            viewers = "864",
            modifier = Modifier.offset(x = 70.dp, y = 14.dp).rotate(8f),
        )
        RoomCard(
            colors = listOf(Color(0xFFA01212), Color(0xFF2A0D0D)),
            viewers = "3.4k",
            modifier = Modifier,
        )
    }
}

@Composable
private fun RoomCard(colors: List<Color>, viewers: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .size(width = 96.dp, height = 128.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(colors))
            .padding(8.dp),
    ) {
        Text(
            "LIVE",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(HivoRed, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        Text("● $viewers", color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomStart))
    }
}
