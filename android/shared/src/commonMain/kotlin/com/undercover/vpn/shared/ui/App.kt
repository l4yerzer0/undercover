package com.undercover.vpn.shared.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.undercover.vpn.shared.ui.theme.DossierTheme

@Composable
fun App() {
    DossierTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Undercover VPN Multiplatform", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
