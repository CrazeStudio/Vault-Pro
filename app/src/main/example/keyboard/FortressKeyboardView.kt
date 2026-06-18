package com.example.keyboard

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.CryptoUtils
import com.example.data.AppDatabase
import com.example.data.model.Credential
import com.example.data.repository.VaultRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class KeyboardLayoutMode {
    LOWERCASE, UPPERCASE, SYMBOLS, ALT_SYMBOLS, VAULT_FILL, MASTER_UNLOCK
}

@Composable
fun FortressKeyboardView(
    onText: (String) -> Unit,
    onDelete: () -> Unit,
    onAction: () -> Unit,
    onSpace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Create direct repository connector for local offline fetching
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { VaultRepository(database.vaultMetadataDao(), database.credentialDao()) }

    var layoutMode by remember { mutableStateOf(KeyboardLayoutMode.LOWERCASE) }
    var passwordQuery by remember { mutableStateOf("") }
    var matchesList by remember { mutableStateOf<List<Credential.Decrypted>>(emptyList()) }
    
    // Unlock input inside keyboard
    var masterPasswordInput by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Read full credentials reactive list when unlocked
    val isVaultUnlocked = CryptoUtils.isUnlocked()
    
    // Refresh or load matched list
    LaunchedEffect(isVaultUnlocked, passwordQuery, layoutMode) {
        if (isVaultUnlocked && layoutMode == KeyboardLayoutMode.VAULT_FILL) {
            repository.getDecryptedCredentials().collect { fullList ->
                matchesList = if (passwordQuery.isBlank()) {
                    fullList
                } else {
                    fullList.filter {
                        it.title.contains(passwordQuery, ignoreCase = true) ||
                        it.url.contains(passwordQuery, ignoreCase = true) ||
                        it.username.contains(passwordQuery, ignoreCase = true)
                    }
                }
            }
        }
    }

    // Modern Dark Slate Palette matching Gboard Theme
    val keyboardBackgroundColor = Color(0xFF1E1E20)
    val keyNormalColor = Color(0xFF323336)
    val keySpecialColor = Color(0xFF1F2022)
    val keyActionButtonColor = Color(0xFF4C8DF5)
    val textOnKeyNormal = Color(0xFFE2E2E2)
    val textOnKeyActionButton = Color(0xFFFFFFFF)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(keyboardBackgroundColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        // --- TOP ROW: FUNCTION BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Shield",
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FORTRESS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (isVaultUnlocked) {
                    if (layoutMode == KeyboardLayoutMode.VAULT_FILL) {
                        // Quick switch to writing mode
                        TextButton(
                            onClick = { layoutMode = KeyboardLayoutMode.LOWERCASE },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00E676))
                        ) {
                            Icon(Icons.Default.Keyboard, contentDescription = "Keyboard", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Type Mode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Quick switch to password autofill
                        Button(
                            onClick = { layoutMode = KeyboardLayoutMode.VAULT_FILL },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676),
                                contentColor = Color(0xFF1E1E20)
                            )
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = "Fill", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Autofill Passwords", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Locked state
                    Button(
                        onClick = {
                            scope.launch {
                                if (repository.hasVaultMetadata()) {
                                    layoutMode = KeyboardLayoutMode.MASTER_UNLOCK
                                } else {
                                    unlockError = "Vault setup required. Open Fortress App first!"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = "Unlock", modifier = Modifier.size(12.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unlock Autofill", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Fast secure password generator button directly on keyboard
                IconButton(
                    onClick = {
                        val newSecurePass = CryptoUtils.generateSecurePassword(16)
                        onText(newSecurePass)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = "Gen",
                        tint = Color(0xFF4C8DF5),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Divider(color = Color(0xFF2C2D30), thickness = 1.dp)

        // --- MIDDLE BODY LAYOUT MODES ---
        AnimatedContent(
            targetState = layoutMode,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "KeyboardLayoutTransition"
        ) { mode ->
            when (mode) {
                KeyboardLayoutMode.VAULT_FILL -> {
                    // Password selector layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        // Search bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .background(Color(0xFF2C2D30), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            TextField(
                                value = passwordQuery,
                                onValueChange = { passwordQuery = it },
                                placeholder = { Text("Search your credentials...", color = Color.Gray, fontSize = 12.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (passwordQuery.isNotEmpty()) {
                                IconButton(onClick = { passwordQuery = "" }, modifier = Modifier.size(18.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        }

                        if (matchesList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (passwordQuery.isBlank()) "No credentials saved in Fortress." else "No passwords match \"$passwordQuery\"",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                items(matchesList) { cred ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .background(Color(0xFF28292C), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val icon = when (cred.category) {
                                                    "Login" -> Icons.Default.AlternateEmail
                                                    "Card" -> Icons.Default.CreditCard
                                                    else -> Icons.Default.Description
                                                }
                                                Icon(icon, contentDescription = "Type", tint = Color(0xFF00E676), modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = cred.title,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = cred.username.ifBlank { "No Username" },
                                                color = Color.LightGray,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row {
                                            Button(
                                                onClick = { onText(cred.username) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C3D40)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .padding(end = 4.dp)
                                            ) {
                                                Text("User", fontSize = 10.sp, color = Color.White)
                                            }
                                            Button(
                                                onClick = { onText(cred.passwordOriginal) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DF5)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("Pass", fontSize = 10.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                KeyboardLayoutMode.MASTER_UNLOCK -> {
                    // Unlock keyboard vault layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Unlock Fortress Keypad 🔓",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Provide master password to unlock autofill items",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C2D30), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp)
                        ) {
                            TextField(
                                value = masterPasswordInput,
                                onValueChange = {
                                    masterPasswordInput = it
                                    unlockError = null
                                },
                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                placeholder = { Text("Master Password", color = Color.Gray, fontSize = 12.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (unlockError != null) {
                            Text(
                                text = unlockError ?: "",
                                color = Color.Red,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row {
                            TextButton(
                                onClick = { layoutMode = KeyboardLayoutMode.LOWERCASE },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = Color.LightGray, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (repository.unlock(masterPasswordInput)) {
                                            layoutMode = KeyboardLayoutMode.VAULT_FILL
                                            masterPasswordInput = ""
                                        } else {
                                            unlockError = "Incorrect password. Try again."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Unlock", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                else -> {
                    // Lowercase / Uppercase / Symbols standard QWERTY keyboards
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                    ) {
                        val rows = when (layoutMode) {
                            KeyboardLayoutMode.LOWERCASE -> listOf(
                                listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
                                listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
                                listOf("SHIFT", "z", "x", "c", "v", "b", "n", "m", "DEL")
                            )
                            KeyboardLayoutMode.UPPERCASE -> listOf(
                                listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
                                listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
                                listOf("CAPS_ACTIVE", "Z", "X", "C", "V", "B", "N", "M", "DEL")
                            )
                            KeyboardLayoutMode.SYMBOLS -> listOf(
                                listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
                                listOf("@", "#", "$", "%", "&", "*", "-", "+", "(", ")"),
                                listOf("ALT_SYM", "!", "\"", "'", ":", ";", "/", "?", "DEL")
                            )
                            KeyboardLayoutMode.ALT_SYMBOLS -> listOf(
                                listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"),
                                listOf("£", "¢", "€", "¥", "^", "°", "=", "_", "{", "}"),
                                listOf("SYM", "\\", "[", "]", "<", ">", "§", "©", "DEL")
                            )
                            else -> emptyList()
                        }

                        // Render layout rows
                        for (rowIndex in rows.indices) {
                            val row = rows[rowIndex]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                for (key in row) {
                                    // Calculate spacing weighting for symmetric offsets
                                    val isSpecialKey = key == "SHIFT" || key == "CAPS_ACTIVE" || key == "DEL" || key == "ALT_SYM" || key == "SYM"
                                    val weight = if (isSpecialKey) 1.45f else 1f
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(weight)
                                            .fillMaxHeight()
                                            .padding(horizontal = 2.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSpecialKey) keySpecialColor else keyNormalColor)
                                            .clickable {
                                                when (key) {
                                                    "SHIFT" -> layoutMode = KeyboardLayoutMode.UPPERCASE
                                                    "CAPS_ACTIVE" -> layoutMode = KeyboardLayoutMode.LOWERCASE
                                                    "SYM" -> layoutMode = KeyboardLayoutMode.SYMBOLS
                                                    "ALT_SYM" -> layoutMode = KeyboardLayoutMode.ALT_SYMBOLS
                                                    "DEL" -> onDelete()
                                                    else -> onText(key)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        when (key) {
                                            "SHIFT" -> Icon(Icons.Default.ArrowUpward, contentDescription = "Shift", tint = textOnKeyNormal, modifier = Modifier.size(16.dp))
                                            "CAPS_ACTIVE" -> Icon(Icons.Default.ArrowUpward, contentDescription = "Caps Active", tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                                            "DEL" -> Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = textOnKeyNormal, modifier = Modifier.size(16.dp))
                                            "ALT_SYM" -> Text("=\\<", color = textOnKeyNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            "SYM" -> Text("?123", color = textOnKeyNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            else -> Text(key, color = textOnKeyNormal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Render Bottom Core Keys Row (Symbols, Comma, Space, Period, Enter/Action)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Symbols toggle (or letters toggle)
                            val symToggleLabel = when (layoutMode) {
                                KeyboardLayoutMode.SYMBOLS, KeyboardLayoutMode.ALT_SYMBOLS -> "ABC"
                                else -> "?123"
                            }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(keySpecialColor)
                                    .clickable {
                                        layoutMode = if (layoutMode == KeyboardLayoutMode.SYMBOLS || layoutMode == KeyboardLayoutMode.ALT_SYMBOLS) {
                                            KeyboardLayoutMode.LOWERCASE
                                        } else {
                                            KeyboardLayoutMode.SYMBOLS
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(symToggleLabel, color = textOnKeyNormal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // Comma key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(keyNormalColor)
                                    .clickable { onText(",") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(",", color = textOnKeyNormal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            // Massive Google Spacebar
                            Box(
                                modifier = Modifier
                                    .weight(4.5f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(keyNormalColor)
                                    .clickable { onSpace() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SpaceBar, contentDescription = "Space", tint = Color.Gray, modifier = Modifier.size(20.dp))
                            }

                            // Period key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(keyNormalColor)
                                    .clickable { onText(".") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(".", color = textOnKeyNormal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            // Enter / Action key blue button
                            Box(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(keyActionButtonColor)
                                    .clickable { onAction() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.KeyboardReturn, contentDescription = "Enter", tint = textOnKeyActionButton, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
