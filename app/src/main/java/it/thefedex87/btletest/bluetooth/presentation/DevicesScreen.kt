package it.thefedex87.btletest.bluetooth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DevicesScreen(
    state: DevicesUiState
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if(state.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.devices) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = it.name ?: "No Name")
                    Spacer(modifier = Modifier.weight(1f))
                    if (it.isConnecting) {
                        CircularProgressIndicator()
                    } else {
                        if (it.isConnected) {
                            Icon(imageVector = Icons.Default.Done, contentDescription = null)
                            Text(text = it.batteryLevel?.toString() ?: "")
                        } else {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        }
                    }
                }
            }
        }

    }
}