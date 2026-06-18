package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.crypto.CryptoUtils
import com.example.data.model.Credential
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainActivityViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) { // Force clean premium dark mode theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F1012)
                ) {
                    FortressApp()
                }
            }
        }
    }
}

enum class DashboardTab {
    VAULT, GENERATOR, KEYBOARD, BACKUP
}

@Composable
fun FortressApp() {
    val context = LocalContext.current
    val viewModel: MainActivityViewModel = viewModel()
    
    val isSetupCompleted by viewModel.isSetupCompleted.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()

    // Screen selection based on state
    AnimatedContent(
        targetState = Pair(isSetupCompleted, isUnlocked),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "MainScreenStateTransition"
    ) { (setup, unlocked) ->
        when {
            !setup -> {
                SetupScreen(onSetup = { pw ->
                    viewModel.initializeVault(pw) { success ->
                        if (success) {
                            Toast.makeText(context, "Secure Vault Initialized!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Setup failed! Try another password.", Toast.LENGTH_LONG).show()
                        }
                    }
                })
            }
            !unlocked -> {
                UnlockScreen(
                    onUnlockSubmit = { pw ->
                        viewModel.unlockVault(pw) { success ->
                            if (success) {
                                Toast.makeText(context, "Access Granted ✔", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Incorrect Master Password", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onSelfDestruct = {
                        viewModel.selfDestructVault()
                        Toast.makeText(context, "Local database wiped completely.", Toast.LENGTH_LONG).show()
                    }
                )
            }
            else -> {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SetupScreen(onSetup: (String) -> Unit) {
    var masterPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var checkUnderstand by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(24.dp))
            
            // Custom designed premium cyber-shield canvas vector
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF1E1F22), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(60.dp)) {
                    drawCircle(brush = Brush.radialGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF))))
                }
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Shield Verified",
                    tint = Color(0xFF0F1012),
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "FORTRESS",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = "Secure Zero-Knowledge Password Vault",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF16171A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Configure Vault Keys",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it; errorMessage = null },
                    label = { Text("Master Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle"
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    label = { Text("Confirm Master Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = checkUnderstand,
                        onCheckedChange = { checkUnderstand = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E676))
                    )
                    Text(
                        text = "I understand Fortress is 100% offline. If I lose my password, it cannot be recovered.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Button(
            onClick = {
                if (masterPassword.length < 6) {
                    errorMessage = "Password must be at least 6 characters."
                } else if (masterPassword != confirmPassword) {
                    errorMessage = "Passwords do not match!"
                } else if (!checkUnderstand) {
                    errorMessage = "Please confirm the offline security state checkbox."
                } else {
                    onSetup(masterPassword)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00E676),
                contentColor = Color(0xFF0F1012)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Security, contentDescription = "Key")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Initialize Secure Vault", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun UnlockScreen(
    onUnlockSubmit: (String) -> Unit,
    onSelfDestruct: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isShaking by remember { mutableStateOf(false) }
    var showDestructDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFF1E1F22), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = Color(0xFFE91E63),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Fortress Locked",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Enter master code to decrypt password store",
                color = Color.Gray,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Master Password") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle"
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF00E676)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (password.isNotEmpty()) {
                        onUnlockSubmit(password)
                        password = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color(0xFF0F1012)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = "Unlock Open")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Decrypt & Access", fontWeight = FontWeight.Bold)
            }
        }

        // Bottom self destruct workflow (for reset/forgot password support)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.navigationBarsPadding()
        ) {
            TextButton(onClick = { showDestructDialog = true }) {
                Text("Forgot Master Password? Reset Vault", color = Color(0xFFFF5252), fontSize = 12.sp)
            }
        }
    }

    if (showDestructDialog) {
        AlertDialog(
            onDismissRequest = { showDestructDialog = false },
            title = { Text("Complete Vault Self-Destruct?") },
            text = {
                Text("Because Fortress uses 100% offline zero-knowledge AES-256 encryption, your password cannot be recovered. If you reset, ALL of your saved usernames, passwords, and backup variables will be permanently destroyed. Are you absolutely sure?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDestructDialog = false
                        onSelfDestruct()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Wipe and Self-Destruct", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDestructDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DashboardScreen(viewModel: MainActivityViewModel) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(DashboardTab.VAULT) }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedItemForEdit by remember { mutableStateOf<Credential.Decrypted?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF16171A),
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = activeTab == DashboardTab.VAULT,
                    onClick = { activeTab = DashboardTab.VAULT },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
                    label = { Text("Logins") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F1012),
                        selectedTextColor = Color(0xFF00E676),
                        indicatorColor = Color(0xFF00E676),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == DashboardTab.GENERATOR,
                    onClick = { activeTab = DashboardTab.GENERATOR },
                    icon = { Icon(Icons.Default.Casino, contentDescription = "Generator") },
                    label = { Text("Generator") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F1012),
                        selectedTextColor = Color(0xFF00E676),
                        indicatorColor = Color(0xFF00E676),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == DashboardTab.KEYBOARD,
                    onClick = { activeTab = DashboardTab.KEYBOARD },
                    icon = { Icon(Icons.Default.Keyboard, contentDescription = "Keyboard") },
                    label = { Text("Keyboard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F1012),
                        selectedTextColor = Color(0xFF00E676),
                        indicatorColor = Color(0xFF00E676),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
                NavigationBarItem(
                    selected = activeTab == DashboardTab.BACKUP,
                    onClick = { activeTab = DashboardTab.BACKUP },
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = "Backup/Sync") },
                    label = { Text("GitHub Sync") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF0F1012),
                        selectedTextColor = Color(0xFF00E676),
                        indicatorColor = Color(0xFF00E676),
                        unselectedIconColor = Color.LightGray,
                        unselectedTextColor = Color.LightGray
                    )
                )
            }
        },
        floatingActionButton = {
            if (activeTab == DashboardTab.VAULT) {
                FloatingActionButton(
                    onClick = {
                        selectedItemForEdit = null
                        showAddEditDialog = true
                    },
                    containerColor = Color(0xFF00E676),
                    contentColor = Color(0xFF0F1012)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Login")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F1012))
        ) {
            when (activeTab) {
                DashboardTab.VAULT -> VaultTab(
                    viewModel = viewModel,
                    onEditRequest = { item ->
                        selectedItemForEdit = item
                        showAddEditDialog = true
                    }
                )
                DashboardTab.GENERATOR -> GeneratorTab()
                DashboardTab.KEYBOARD -> KeyboardTab()
                DashboardTab.BACKUP -> BackupTab(viewModel = viewModel)
            }
        }
    }

    if (showAddEditDialog) {
        CredentialAddEditDialog(
            item = selectedItemForEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { title, username, password, url, notes, category, favorite ->
                viewModel.saveCredential(
                    id = selectedItemForEdit?.id ?: 0,
                    title = title,
                    username = username,
                    passwordRaw = password,
                    url = url,
                    notes = notes,
                    category = category,
                    isFavorite = favorite
                ) { success ->
                    if (success) {
                        Toast.makeText(context, "Saved Successfully", Toast.LENGTH_SHORT).show()
                        showAddEditDialog = false
                    } else {
                        Toast.makeText(context, "Encryption error saving item", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
fun VaultTab(
    viewModel: MainActivityViewModel,
    onEditRequest: (Credential.Decrypted) -> Unit
) {
    val credentialsList by viewModel.credentials.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val filteredList = remember(credentialsList, searchQuery, selectedCategoryFilter) {
        credentialsList.filter { item ->
            val matchesQuery = item.title.contains(searchQuery, ignoreCase = true) ||
                    item.url.contains(searchQuery, ignoreCase = true) ||
                    item.username.contains(searchQuery, ignoreCase = true) ||
                    item.notes.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = selectedCategoryFilter == "All" || item.category == selectedCategoryFilter
            matchesQuery && matchesCategory
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        // App header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Shield Verified Logo",
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FORTRESS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            IconButton(onClick = { viewModel.lockVault() }) {
                Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Color.Gray)
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search logins & cards...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "SearchIcon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00E676),
                unfocusedBorderColor = Color(0xFF2C2D30)
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // Horizontal Category selector chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf("All", "Login", "Card", "Secure Note")
            for (category in categories) {
                val isSelected = selectedCategoryFilter == category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) Color(0xFF00E676) else Color(0xFF16171A))
                        .clickable { selectedCategoryFilter = category }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) Color.Black else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Empty",
                        tint = Color.Gray,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No credentials cached in Fortress",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList) { item ->
                    CredentialItemRow(
                        item = item,
                        onClick = { onEditRequest(item) },
                        onDeleteClick = { viewModel.deleteCredential(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun CredentialItemRow(
    item: Credential.Decrypted,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var isPassVisible by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16171A))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon background indicator
            val catIcon = when (item.category) {
                "Login" -> Icons.Default.AlternateEmail
                "Card" -> Icons.Default.CreditCard
                else -> Icons.Default.Description
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFF1E2124), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(catIcon, contentDescription = "Type", tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isFavorite) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Star, contentDescription = "Fav", tint = Color(0xFFFFC107), modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.username.ifBlank { "No username/identifier" },
                    color = Color.Gray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isPassVisible) item.passwordOriginal else "••••••••••••",
                    color = Color(0xFF00E676),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { isPassVisible = !isPassVisible }) {
                Icon(
                    imageVector = if (isPassVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Show/Hide Password",
                    tint = Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(item.passwordOriginal))
                Toast.makeText(context, "Password Copied Securely ✔", Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Password",
                    tint = Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Credential?") },
            text = { Text("Are you sure you want to permanently delete \"${item.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GeneratorTab() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var passwordLength by remember { mutableFloatStateOf(16f) }
    var useUppercase by remember { mutableStateOf(true) }
    var useLowercase by remember { mutableStateOf(true) }
    var useNumbers by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }

    var generatedPassword by remember { mutableStateOf("") }

    fun regen() {
        generatedPassword = CryptoUtils.generateSecurePassword(
            length = passwordLength.toInt(),
            includeUppercase = useUppercase,
            includeLowercase = useLowercase,
            includeNumbers = useNumbers,
            includeSymbols = useSymbols
        )
    }

    LaunchedEffect(passwordLength, useUppercase, useLowercase, useNumbers, useSymbols) {
        regen()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Interactive Password Generator",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        // Master display cards
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = Color(0xFF16171A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = generatedPassword.ifBlank { "..." },
                    color = Color(0xFF00E676),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Character entropy metric bar
                val lengthValue = passwordLength.toInt()
                val color = when {
                    lengthValue < 10 -> Color(0xFFFF5252)
                    lengthValue < 14 -> Color(0xFFFFC107)
                    else -> Color(0xFF00E676)
                }
                val metricText = when {
                    lengthValue < 10 -> "Weak (Low Entropy)"
                    lengthValue < 14 -> "Decent (Moderate)"
                    else -> "Extremely Secure (High Entropy)"
                }

                Text(metricText, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (passwordLength - 6) / 58f },
                    color = color,
                    trackColor = Color(0xFF2C2D30),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(6.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    TextButton(onClick = { regen() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regen")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate")
                    }
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(generatedPassword))
                            Toast.makeText(context, "Copied securely to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy String")
                    }
                }
            }
        }

        // Configuration list
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF16171A))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Password Length", color = Color.White, fontSize = 13.sp)
                    Text("${passwordLength.toInt()} Chars", color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = passwordLength,
                    onValueChange = { passwordLength = it },
                    valueRange = 6f..64f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF00E676),
                        inactiveTrackColor = Color(0xFF2C2D30),
                        thumbColor = Color(0xFF00E676)
                    )
                )
            }

            Divider(color = Color(0xFF232426))

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Letters (A - Z)", color = Color.White, fontSize = 13.sp)
                Switch(checked = useUppercase, onCheckedChange = { useUppercase = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676)))
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Letters (a - z)", color = Color.White, fontSize = 13.sp)
                Switch(checked = useLowercase, onCheckedChange = { useLowercase = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676)))
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Numbers (0 - 9)", color = Color.White, fontSize = 13.sp)
                Switch(checked = useNumbers, onCheckedChange = { useNumbers = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676)))
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Symbols (!@#$...)", color = Color.White, fontSize = 13.sp)
                Switch(checked = useSymbols, onCheckedChange = { useSymbols = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676)))
            }
        }
    }
}

@Composable
fun KeyboardTab() {
    val context = LocalContext.current
    var inputTestText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Fortress System Keyboard",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Enable system keyboard integration to search, select, and inject passwords securely into other apps (like Chrome) with 100% zero-knowledge safety.",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Visual setup guide card
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF16171A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("How to Setup Keyboard Flow:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text("1.", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                    Text("Tap **Enable Keyboard Settings** below and toggle on the \"Fortress Keyboard\" switch.", color = Color.LightGray, fontSize = 11.sp, lineHeight = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text("2.", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                    Text("Tap **Switch System Keyboard** and choose Fortress as your active virtual input method.", color = Color.LightGray, fontSize = 11.sp, lineHeight = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text("3.", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(22.dp))
                    Text("Tap the green \"Autofill Passwords\" key on the top row of the keyboard to automatically paste passwords anywhere!", color = Color.LightGray, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }

        // Live test input box
        OutlinedTextField(
            value = inputTestText,
            onValueChange = { inputTestText = it },
            placeholder = { Text("Tape here to test your keyboard!", color = Color.Gray) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF00E676)
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open settings. Search Virtual Keyboards manually.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DF5), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("1. Enable Keyboard", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    try {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showInputMethodPicker()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Soft keyboard switcher is locked by device configuration.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Language, contentDescription = "Select", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("2. Select Keyboard", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BackupTab(viewModel: MainActivityViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val repo by viewModel.gitHubRepo.collectAsState()
    val path by viewModel.gitHubPath.collectAsState()
    val token by viewModel.gitHubToken.collectAsState()
    
    val syncStatus by viewModel.syncStatus.collectAsState()
    val syncIsLoading by viewModel.syncIsLoading.collectAsState()

    var inputPassphrase by remember { mutableStateOf("") }
    var inputRepo by remember { mutableStateOf(repo) }
    var inputPath by remember { mutableStateOf(path) }
    var inputToken by remember { mutableStateOf(token) }

    var pasteBackupPayload by remember { mutableStateOf("") }
    var backupPassPhrase by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "E2EE Cloud Sync & backups",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Keep your passwords everywhere. Generate strong encrypted files and sync safely to your private/public GitHub repository.",
            color = Color.Gray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF16171A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "GitHub API configuration",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = inputRepo,
                    onValueChange = { inputRepo = it },
                    label = { Text("Repository Path (owner/repo)") },
                    placeholder = { Text("e.g. janesmith/passwords") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676)
                    )
                )

                OutlinedTextField(
                    value = inputPath,
                    onValueChange = { inputPath = it },
                    label = { Text("File Path inside Repository") },
                    placeholder = { Text("e.g. fortress_vault.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676)
                    )
                )

                OutlinedTextField(
                    value = inputToken,
                    onValueChange = { inputToken = it },
                    label = { Text("GitHub PAT (Token with repo scope)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676)
                    )
                )

                Button(
                    onClick = {
                        viewModel.updateGitHubConfig(inputRepo, inputPath, inputToken)
                        Toast.makeText(context, "GitHub Connection configurations saved!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C3D40))
                ) {
                    Text("Save Config", fontSize = 11.sp, color = Color.White)
                }

                Divider(color = Color(0xFF2C2D30))

                Text(
                    text = "Sync Operation (Master Encryption)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = inputPassphrase,
                    onValueChange = { inputPassphrase = it },
                    label = { Text("Master Encryption Passcode") },
                    placeholder = { Text("Encrypts backup file before pushing") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E676)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.pushVaultToGitHub(inputPassphrase)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        enabled = !syncIsLoading,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (syncIsLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black)
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Push", modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Push Backup", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.pullVaultFromGitHub(inputPassphrase)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C8DF5)),
                        enabled = !syncIsLoading,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (syncIsLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Pull", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pull Restore", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (syncStatus.isNotEmpty()) {
                    Text(
                        text = syncStatus,
                        color = if (syncStatus.startsWith("Success")) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun CredentialAddEditDialog(
    item: Credential.Decrypted?,
    onDismiss: () -> Unit,
    onSave: (title: String, username: String, passwordRaw: String, url: String, notes: String, category: String, isFavorite: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(item?.title ?: "") }
    var username by remember { mutableStateOf(item?.username ?: "") }
    var password by remember { mutableStateOf(item?.passwordOriginal ?: "") }
    var url by remember { mutableStateOf(item?.url ?: "") }
    var notes by remember { mutableStateOf(item?.notes ?: "") }
    var category by remember { mutableStateOf(item?.category ?: "Login") }
    var isFavorite by remember { mutableStateOf(item?.isFavorite ?: false) }

    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = if (item == null) "Add New Item 🔑" else "Edit Item Details",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Category selector block
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val activeCats = listOf("Login", "Card", "Secure Note")
                        for (cat in activeCats) {
                            val activeVal = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (activeVal) Color(0xFF00E676) else Color(0xFF2C2D30))
                                    .clickable { category = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 10.sp,
                                    color = if (activeVal) Color.Black else Color.LightGray,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title / Site Domain") },
                        placeholder = { Text("e.g. Google, Chase Bank") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676)
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username / Email ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676)
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password Value") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Row {
                                IconButton(onClick = { password = CryptoUtils.generateSecurePassword(16) }) {
                                    Icon(Icons.Default.Casino, contentDescription = "Gen", tint = Color(0xFF00E676))
                                }
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle"
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676)
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Website Address / URL") },
                        placeholder = { Text("e.g. https://accounts.google.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676)
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Security Notes") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF00E676)
                        )
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isFavorite,
                            onCheckedChange = { isFavorite = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E676))
                        )
                        Text("Add to Favorites Folder", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        title = "Security Credential Listing"
                    }
                    onSave(title, username, password, url, notes, category, isFavorite)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black)
            ) {
                Text("Save Securely", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss() },
                colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
            ) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFF16171A)
    )
}
