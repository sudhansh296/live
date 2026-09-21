package com.coderlobby.hivo

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.coderlobby.hivo.auth.AuthViewModel
import com.coderlobby.hivo.data.AppContainer
import com.coderlobby.hivo.ui.AppRoot
import com.coderlobby.hivo.ui.theme.HivoTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as HivoApp).container

    private val authViewModel: AuthViewModel by viewModels {
        val container = container
        viewModelFactory { initializer { AuthViewModel(container) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark: light status/navigation bar icons on a transparent bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HivoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(authViewModel, container)
                }
            }
        }
    }
}
