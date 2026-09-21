package com.coderlobby.hivo.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coderlobby.hivo.R
import com.coderlobby.hivo.auth.Session

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HivoLogoTile()
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
    }
}

// Temporary: replaced by the real Home once profile setup (Page 2) and the tabs exist.
@Composable
fun SignedInScreen(session: Session.SignedIn, onSignOut: () -> Unit) {
    val user = session.user
    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HivoLogoTile(size = 64)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.signed_in_title), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            user.phoneMasked ?: user.email.orEmpty(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            stringResource(if (user.profileCompleted) R.string.signed_in_ready else R.string.signed_in_next),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
        )
        OutlinedButton(onClick = onSignOut) {
            Text(stringResource(R.string.sign_out), color = Color.White)
        }
    }
}
