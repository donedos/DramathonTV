package com.dramathon.dramathonplus.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import com.dramathon.dramathonplus.ui.theme.Accent
import com.dramathon.dramathonplus.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CodeEntryDialog(
    onDismiss: () -> Unit,
    onUnlock: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }
    val unlockFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Once the code is complete the keyboard is closed, otherwise it keeps the
    // d-pad and the buttons out of reach on a TV.
    LaunchedEffect(code) {
        if (code.length == 6) {
            keyboardController?.hide()
            delay(300)
            try {
                unlockFocusRequester.requestFocus()
            } catch (_: Exception) {
                // The button may not be attached yet, the field keeps the focus.
            }
        }
    }

    val submitCode = {
        if (code.length == 6) {
            verifying = true
            error = null
            onUnlock(code) { success, msg ->
                verifying = false
                if (success) onDismiss()
                else error = msg
            }
        } else {
            error = "Please enter a 6-character code."
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(top = 28.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .width(480.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)))
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🔓 UNLOCK ALL EPISODES",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Enter your 6-character pass code below:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // 6-Character OTP Cells Representation
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Invisible TextField handling input & keyboard
                        BasicTextField(
                            value = code,
                            onValueChange = { input ->
                                if (input.length <= 6) {
                                    code = input.uppercase().filter { it.isLetterOrDigit() }
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitCode() }
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .focusProperties { down = cancelFocusRequester }
                                .matchParentSize()
                        )

                        // 6 Visible Custom Character Cells
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until 6) {
                                val char = code.getOrNull(i)?.toString() ?: ""
                                val isFocusedCell = i == code.length

                                Box(
                                    modifier = Modifier
                                        .size(width = 56.dp, height = 64.dp)
                                        .background(
                                            color = if (isFocusedCell) Accent.copy(alpha = 0.15f)
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            border = BorderStroke(
                                                width = if (isFocusedCell) 2.dp else 1.dp,
                                                color = if (isFocusedCell) Accent
                                                        else if (char.isNotEmpty()) Accent.copy(alpha = 0.5f)
                                                        else Color.White.copy(alpha = 0.1f)
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { focusRequester.requestFocus() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (char.isNotEmpty()) Accent else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    if (error != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error!!,
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = TextSecondary
                            ),
                            modifier = Modifier
                                .focusRequester(cancelFocusRequester)
                                .focusProperties {
                                    up = focusRequester
                                    right = unlockFocusRequester
                                }
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = { submitCode() },
                            enabled = !verifying && code.length == 6,
                            colors = ButtonDefaults.colors(containerColor = Accent),
                            modifier = Modifier
                                .focusRequester(unlockFocusRequester)
                                .focusProperties {
                                    up = focusRequester
                                    left = cancelFocusRequester
                                }
                        ) {
                            if (verifying) {
                                CustomProgressIndicator(size = 24.dp)
                            } else {
                                Text("Unlock Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
