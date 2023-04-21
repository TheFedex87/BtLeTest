package it.thefedex87.btletest.bluetooth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onConnectRequested: () -> Unit,
    onDisconnectRequested: () -> Unit,
    onWriteRequested: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                state.devices.forEach {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp)
                    ) {
                        Text(text = it.name ?: it.address)
                        Spacer(modifier = Modifier.weight(1f))
                        if (it.isConnecting) {
                            CircularProgressIndicator()
                        } else {
                            if (it.isConnected) {
                                Icon(imageVector = Icons.Default.Done, contentDescription = null)
                                Text(text = it.batteryLevel?.toString() ?: "")
                            } else {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = if (state.connectionState == ConnectionState.CONNECTED) onDisconnectRequested else onConnectRequested,
                    enabled = state.connectionState != ConnectionState.REQUESTED,
                    modifier = Modifier.padding(2.dp)
                ) {
                    Text(text = if (state.connectionState == ConnectionState.CONNECTED) "Disconnect" else "Connect")
                }

                Button(
                    onClick = onWriteRequested,
                    enabled = state.connectionState == ConnectionState.CONNECTED
                ) {
                    Text(text = "Write")
                }
            }
        }
    }
}