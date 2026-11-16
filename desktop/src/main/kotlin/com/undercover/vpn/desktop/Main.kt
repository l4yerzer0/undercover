package com.undercover.vpn.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.undercover.vpn.desktop.core.SingBoxCoreManager
import com.undercover.vpn.desktop.core.StorageManager
import com.undercover.vpn.desktop.model.VpnState
import com.undercover.vpn.desktop.ui.DesktopApp
import kotlinx.coroutines.launch

object TrayIconPainter : Painter() {
    override val intrinsicSize: Size = Size(32f, 32f)
    override fun DrawScope.onDraw() {
        drawRect(Color(0xFF8FE3B0))
    }
}

fun main() = application {
    var isVisible by remember { mutableStateOf(true) }
    val windowState = rememberWindowState(width = 520.dp, height = 760.dp)
    val vpnState by SingBoxCoreManager.vpnState.collectAsState()
    val scope = rememberCoroutineScope()

    val trayTooltip = remember(vpnState) {
        when (vpnState) {
            is VpnState.Connected -> "Undercover VPN: Connected"
            is VpnState.Connecting -> "Undercover VPN: Connecting..."
            else -> "Undercover VPN: Disconnected"
        }
    }

    // System Tray
    Tray(
        icon = TrayIconPainter,
        tooltip = trayTooltip,
        onAction = { isVisible = true },
        menu = {
            Item("Show Window", onClick = { isVisible = true })
            Separator()
            if (vpnState is VpnState.Connected) {
                Item("Disconnect", onClick = { scope.launch { SingBoxCoreManager.stop() } })
            }
            Item("Exit", onClick = {
                scope.launch {
                    SingBoxCoreManager.stop()
                    exitApplication()
                }
            })
        }
    )

    if (isVisible) {
        Window(
            onCloseRequest = {
                val settings = StorageManager.loadSettings()
                if (settings.minimizeToTray) {
                    isVisible = false
                } else {
                    scope.launch {
                        SingBoxCoreManager.stop()
                        exitApplication()
                    }
                }
            },
            title = "Undercover VPN",
            state = windowState
        ) {
            DesktopApp()
        }
    }
}
