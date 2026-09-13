package com.dramathon.dramathonplus.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.tv.material3.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dramathon.dramathonplus.ui.theme.Accent
import com.dramathon.dramathonplus.ui.theme.TextSecondary
import androidx.compose.ui.window.Dialog

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CodeEntryDialog(
    onDismiss: () -> Unit,
    onUnlock: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(400.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔓 Unlock All Episodes",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Enter your 6-character code to unlock all episodes.",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = code,
                    onValueChange = { 
                        if (it.length <= 6) {
                            code = it.uppercase().filter { char -> char.isLetterOrDigit() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("6-character code") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false
                    ),
                    singleLine = true
                )
                
                if (error != null) {
                    Text(
                        text = error!!, 
                        color = Color.Red, 
                        fontSize = 14.sp, 
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent, 
                            contentColor = TextSecondary
                        )
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            if (code.length == 6) {
                                verifying = true
                                error = null
                                onUnlock(code) { success, msg ->
                                    verifying = false
                                    if (success) onDismiss()
                                    else error = msg
                                }
                            } else {
                                error = "Please enter 6-character code."
                            }
                        },
                        enabled = !verifying && code.length == 6,
                        colors = ButtonDefaults.colors(containerColor = Accent)
                    ) {
                        if (verifying) {
                            CustomProgressIndicator(size = 32.dp)
                        } else {
                            Text("Unlock Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
