package com.example.ui.screens

import android.speech.tts.TextToSpeech
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ChatMessage
import com.example.data.ChatSession
import com.example.ui.theme.ElectricGlow
import com.example.ui.theme.PikachuOrange
import com.example.ui.theme.PikachuYellow
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sessions by viewModel.allSessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val messages by viewModel.currentMessages.collectAsState()
    val isGenerating by viewModel.isGeneratingResponse.collectAsState()
    val attachedFile by viewModel.currentAttachedFile.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var showFilePickerDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<ChatSession?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // TextToSpeech initialization
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val initializedTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        initializedTts.language = Locale.US
        tts = initializedTts

        onDispose {
            initializedTts.stop()
            initializedTts.shutdown()
        }
    }

    fun speak(text: String) {
        if (isTtsReady && tts != null) {
            // strip simple markdown characters before speaking for clean voice output
            val cleanText = text
                .replace("*", "")
                .replace("#", "")
                .replace("`", "")
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
            Toast.makeText(context, "Speaking: Pika-pika! ⚡", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Voice utility loading...", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = PikachuYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "LEARNING FLOORS",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close menu")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // "New Session" buttons
                    Button(
                        onClick = {
                            viewModel.createSession(
                                title = "New Session",
                                academicLevel = userProfile?.academicLevel ?: "High School",
                                studyMode = userProfile?.studyMode ?: "Standard"
                            )
                            scope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PikachuYellow,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("new_chat_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("New Session", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "RECENT LESSONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Lazy List of Sessions
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions) { session ->
                            val isSelected = session.id == currentSessionId
                            Card(
                                onClick = {
                                    viewModel.selectSession(session.id)
                                    scope.launch { drawerState.close() }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) PikachuYellow.copy(alpha = 0.15f) else Color.Transparent
                                ),
                                border = if (isSelected) BorderStroke(1.dp, PikachuYellow) else null,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                viewModel.selectSession(session.id)
                                                scope.launch { drawerState.close() }
                                            },
                                            onLongClick = {
                                                renameInput = session.title
                                                showRenameDialog = session
                                            }
                                        )
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (session.isPinned) Icons.Default.PushPin else Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = if (isSelected) PikachuOrange else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = session.title,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${session.studyMode} • ${session.academicLevel}",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                            )
                                        }
                                    }

                                    // Action icons (rename or delete)
                                    IconButton(
                                        onClick = { viewModel.deleteSession(session.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete chat",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Profile & Settings quick integrations
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigateProfile()
                                scope.launch { drawerState.close() }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = PikachuYellow)
                        Column {
                            Text(userProfile?.fullName ?: "Profile", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(userProfile?.email ?: "ash@pallettown.com", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onNavigateSettings()
                                scope.launch { drawerState.close() }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = null, tint = Color.Gray)
                        Text("Settings & Modes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        content = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            val activeSession = sessions.find { it.id == currentSessionId }
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = activeSession?.title ?: "Pikachu AI Chat",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Mode: ${activeSession?.studyMode ?: "Standard"} (${activeSession?.academicLevel ?: "High School"})",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { stopSpeaking() }) {
                                Icon(imageVector = Icons.Default.VolumeMute, contentDescription = "Stop sound", tint = MaterialTheme.colorScheme.error)
                            }
                            IconButton(onClick = onNavigateBack) {
                                Icon(imageVector = Icons.Default.Home, contentDescription = "Home")
                            }
                        }
                    )
                },
                modifier = modifier
            ) { paddingValues ->
                val listState = rememberLazyListState()

                // Auto Scroll to last message whenever list inserts
                LaunchedEffect(messages.size, isGenerating) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // MAIN CHAT MESSAGE LIST
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (messages.isEmpty()) {
                            // Blank empty state greeting
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(PikachuYellow.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = PikachuOrange,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Pika-Pika! Ready to Explore?",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Text(
                                    text = "Ask me anything about Biology, Math equations, History dates or React codes. Tap the Paperclip to upload notes!",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "TAP A STUDY SHORTCUT CHIP BELOW TO START",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PikachuOrange,
                                    letterSpacing = 1.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(messages) { message ->
                                    val isAssistant = message.role == "assistant"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top,
                                            modifier = Modifier.fillMaxWidth(0.85f)
                                        ) {
                                            if (isAssistant) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(PikachuYellow, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ElectricBolt,
                                                        contentDescription = "Pikachu icon",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Column(horizontalAlignment = if (isAssistant) Alignment.Start else Alignment.End) {
                                                // Message block card
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = if (isAssistant) {
                                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                                        } else {
                                                            PikachuYellow.copy(alpha = 0.15f)
                                                        }
                                                    ),
                                                    shape = RoundedCornerShape(
                                                        topStart = 16.dp,
                                                        topEnd = 16.dp,
                                                        bottomStart = if (isAssistant) 4.dp else 16.dp,
                                                        bottomEnd = if (isAssistant) 16.dp else 4.dp
                                                    ),
                                                    border = if (!isAssistant) BorderStroke(1.dp, PikachuYellow.copy(alpha = 0.4f)) else null
                                                ) {
                                                    Column(modifier = Modifier.padding(14.dp)) {
                                                        // File details if attached
                                                        if (message.attachedFileName != null) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .padding(bottom = 8.dp)
                                                                    .background(
                                                                        MaterialTheme.colorScheme.surface,
                                                                        RoundedCornerShape(8.dp)
                                                                    )
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.FileOpen,
                                                                    contentDescription = null,
                                                                    tint = PikachuOrange,
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                                Text(
                                                                    text = message.attachedFileName,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = PikachuOrange
                                                                )
                                                            }
                                                        }

                                                        Text(
                                                            text = message.content,
                                                            fontSize = 14.sp,
                                                            lineHeight = 18.sp,
                                                            color = MaterialTheme.colorScheme.onBackground
                                                        )
                                                    }
                                                }

                                                // Message footnotes details (speaking voice and relative timestamp)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                ) {
                                                    Text(
                                                        text = DateUtils.getRelativeTimeSpanString(message.timestamp).toString(),
                                                        fontSize = 9.sp,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                                    )

                                                    if (isAssistant) {
                                                        IconButton(
                                                            onClick = { speak(message.content) },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.VolumeUp,
                                                                contentDescription = "Speak aloud",
                                                                tint = PikachuYellow,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (!isAssistant) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Person,
                                                        contentDescription = "User",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Pulsing / loader indicator when AI generating response
                        if (isGenerating) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .shadow(6.dp, RoundedCornerShape(100.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(100.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = PikachuYellow,
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "⚡ Pikachu AI is compiling concepts...",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PikachuOrange
                                    )
                                }
                            }
                        }
                    }

                    // BOTTOM BAR AREA (Pre-chips inputs combined with file attaching)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Quick Study Chips above input
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudySuggestionChip(
                                label = "📝 Summarize Note",
                                onClick = {
                                    textInput = ""
                                    textInput = "Can you please generate a bulleted summary of this topic?"
                                }
                            )
                            StudySuggestionChip(
                                label = "🧬 Explain DNA Structures",
                                onClick = {
                                    textInput = ""
                                    textInput = "Teach me the double-helix structures of DNA step-by-step with analogies."
                                }
                            )
                            StudySuggestionChip(
                                label = "📐 Solve Quadratic formula",
                                onClick = {
                                    textInput = ""
                                    textInput = "Explain how the quadratic formula works and solve x^2 - 5x + 6 = 0."
                                }
                            )
                        }

                        // Display active attached file alert
                        if (attachedFile != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .background(PikachuYellow.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .border(1.dp, PikachuYellow, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Attachment,
                                        contentDescription = "Attached",
                                        tint = PikachuOrange
                                    )
                                    Column {
                                        Text(
                                            text = attachedFile!!.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "${attachedFile!!.type} • Ingested context securely",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.clearAttachedFile() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove file",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Input control bar row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { showFilePickerDialog = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Attach file",
                                    tint = PikachuOrange
                                )
                            }

                            TextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = {
                                    Text(
                                        "Ask Pikachu AI anything...",
                                        fontSize = 13.sp,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp)),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                maxLines = 3
                            )

                            // Quick speech trigger
                            IconButton(
                                onClick = {
                                    if (textInput.isBlank()) {
                                        textInput = "Pika-pika! Give me a random motivational study tip!"
                                    } else {
                                        textInput += " (Pikachu, speak clearly)"
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Mic helper",
                                    tint = Color.Gray
                                )
                            }

                            Button(
                                onClick = {
                                    if (textInput.trim().isNotEmpty() || attachedFile != null) {
                                        viewModel.sendMessage(textInput.trim())
                                        textInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PikachuYellow,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("submit_button"),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Default.Send,
                                    contentDescription = "Send"
                                )
                            }
                        }
                    }
                }
            }
        }
    )

    // FILE PICKER SELECTION DIALOG (Simulating high-value document uploads)
    if (showFilePickerDialog) {
        Dialog(onDismissRequest = { showFilePickerDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(PikachuYellow.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = PikachuOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "Upload Study Document",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "Identify academic files or attach our curated lecture notes to run calculations immediately:",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CuratedDocumentItem(
                            title = "Biology Notes (Cellular Biology).pdf",
                            desc = "Notes containing Mitochondria, DNA replication loops, details",
                            onClick = {
                                viewModel.attachFile(
                                    "Biology Notes (Cellular Biology).pdf",
                                    "Mitochondria is the powerhouse of the cell. DNA undergoes transcription in the nucleus. Ribosomes translate RNA into cellular proteins recursively.",
                                    "PDF"
                                )
                                showFilePickerDialog = false
                            }
                        )

                        CuratedDocumentItem(
                            title = "Algebra Formula Guide.docx",
                            desc = "Formula notes on Quadratic and polynomial resolutions",
                            onClick = {
                                viewModel.attachFile(
                                    "Algebra Formula Guide.docx",
                                    "Quadratic formula: x = (-b +/- sqrt(b^2 - 4ac)) / 2a. Polynomial roots are solved using synthetic divisions of coefficients.",
                                    "DOCX"
                                )
                                showFilePickerDialog = false
                            }
                        )

                        CuratedDocumentItem(
                            title = "Simple Code Guide.txt",
                            desc = "Python cycles and Kotlin loops cheatsheet",
                            onClick = {
                                viewModel.attachFile(
                                    "Simple Code Guide.txt",
                                    "Kotlin loops: for (item in list) { print(item) }.\nPython iterates with: for item in list:\n\tprint(item)",
                                    "TXT"
                                )
                                showFilePickerDialog = false
                            }
                        )
                    }

                    TextButton(onClick = { showFilePickerDialog = false }) {
                        Text("Cancel", color = PikachuOrange)
                    }
                }
            }
        }
    }

    // SESSION RENAME CONVERSATION DIALOG
    if (showRenameDialog != null) {
        Dialog(onDismissRequest = { showRenameDialog = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Rename Study Session",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    TextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showRenameDialog = null }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (renameInput.isNotBlank()) {
                                    viewModel.updateSessionTitle(showRenameDialog!!.id, renameInput.trim())
                                    showRenameDialog = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PikachuYellow, contentColor = Color.Black)
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudySuggestionChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(100.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun CuratedDocumentItem(
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = PikachuYellow,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = desc,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    }
}
