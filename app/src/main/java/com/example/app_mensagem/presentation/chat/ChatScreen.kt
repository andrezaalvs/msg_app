package com.example.app_mensagem.presentation.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.app_mensagem.R
import com.example.app_mensagem.data.model.Message
import com.example.app_mensagem.presentation.viewmodel.ChatViewModel
import com.example.app_mensagem.ui.theme.chatGradient
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.net.URL

enum class SearchMode { TEXT, DATE }

data class AudioPlaybackState(
    val playingMessageId: String?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val positionMs: Long
)

@Composable
private fun RecordingIndicator() {
    val start = remember { System.currentTimeMillis() }
    var elapsedSec by remember { mutableIntStateOf(0) }

    val infinite = rememberInfiniteTransition(label = "rec_pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), repeatMode = RepeatMode.Reverse),
        label = "rec_alpha"
    )

    LaunchedEffect(Unit) {
        while (true) {
            elapsedSec = (((System.currentTimeMillis() - start) / 1000L).toInt()).coerceAtLeast(0)
            kotlinx.coroutines.delay(250)
        }
    }

    val mm = (elapsedSec / 60).toString().padStart(2, '0')
    val ss = (elapsedSec % 60).toString().padStart(2, '0')

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Gravando áudio • $mm:$ss",
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Toque no stop para enviar",
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    navController: NavController, 
    conversationId: String?,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit
) {
    val chatViewModel: ChatViewModel = viewModel()
    val uiState by chatViewModel.uiState.collectAsState()
    val isGroupChat = uiState.conversation?.isGroup ?: false
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var showMenu by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(SearchMode.TEXT) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }

    // --- ÁUDIO: player único para a conversa ---
    val audioPlayer = remember { ExoPlayer.Builder(context).build() }
    var playingAudioMessageId by remember { mutableStateOf<String?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioDurationMs by remember { mutableLongStateOf(0L) }
    var audioPositionMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { audioPlayer.release() }
    }

    LaunchedEffect(playingAudioMessageId) {
        while (playingAudioMessageId != null) {
            audioDurationMs = audioPlayer.duration.coerceAtLeast(0L)
            audioPositionMs = audioPlayer.currentPosition.coerceAtLeast(0L)
            isAudioPlaying = audioPlayer.isPlaying
            kotlinx.coroutines.delay(200)
        }
    }

    LaunchedEffect(uiState.error) {
        val msg = uiState.error
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            chatViewModel.clearError()
        }
    }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }

    fun createCaptureUri(prefix: String, extension: String): Uri {
        val baseDir = context.externalCacheDir ?: context.cacheDir
        val directory = File(baseDir, "media").apply { mkdirs() }
        val file = File(directory, "${prefix}_${System.currentTimeMillis()}.$extension")
        return FileProvider.getUriForFile(context, "com.example.app_mensagem.fileprovider", file)
    }

    // --- LAUNCHERS ---

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, "Permissão de câmera negada.", Toast.LENGTH_SHORT).show()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(context, "Permissão de microfone negada.", Toast.LENGTH_SHORT).show()
    }

    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && conversationId != null) {
            val type = context.contentResolver.getType(uri) ?: ""
            val mediaType = if (type.startsWith("video")) "VIDEO" else "IMAGE"
            chatViewModel.sendMediaMessage(conversationId, uri, mediaType, uiState.conversation?.isGroup ?: false)
        }
    }

    val docLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && conversationId != null) {
            chatViewModel.sendMediaMessage(conversationId, uri, "DOCUMENT", uiState.conversation?.isGroup ?: false)
        }
    }

    val cameraPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (success && conversationId != null && uri != null) {
            chatViewModel.sendMediaMessage(conversationId, uri, "IMAGE", uiState.conversation?.isGroup ?: false)
        }
    }

    val cameraVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        val uri = pendingVideoUri
        pendingVideoUri = null
        if (success && conversationId != null && uri != null) {
            chatViewModel.sendMediaMessage(conversationId, uri, "VIDEO", uiState.conversation?.isGroup ?: false)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            if (conversationId != null) chatViewModel.sendLocation(conversationId)
        }
    }

    LaunchedEffect(conversationId) {
        if (conversationId != null) chatViewModel.loadMessages(conversationId)
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.size - 1)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (uiState.isUploadingMedia) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            // --- CABEÇALHO ---
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    AnimatedVisibility(visible = isSearching) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { 
                                    isSearching = false
                                    searchQuery = ""
                                    if (conversationId != null) chatViewModel.searchMessages(conversationId, "")
                                }) { Icon(Icons.Default.Close, null) }
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { 
                                        searchQuery = it
                                        if (conversationId != null) chatViewModel.searchMessages(conversationId, it, searchMode == SearchMode.DATE) 
                                    },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { 
                                        Text(if (searchMode == SearchMode.TEXT) "Pesquisar mensagem..." else "Data (dd/mm/yyyy hh:mm)...") 
                                    },
                                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                                FilterChip(
                                    selected = searchMode == SearchMode.TEXT,
                                    onClick = { searchMode = SearchMode.TEXT; if (conversationId != null) chatViewModel.searchMessages(conversationId, searchQuery, false) },
                                    label = { Text("Texto") }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FilterChip(
                                    selected = searchMode == SearchMode.DATE,
                                    onClick = { searchMode = SearchMode.DATE; if (conversationId != null) chatViewModel.searchMessages(conversationId, searchQuery, true) },
                                    label = { Text("Data/Hora") }
                                )
                            }
                        }
                    }

                    if (!isSearching) {
                        Row(
                            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                                AsyncImage(
                                    model = uiState.conversation?.profilePictureUrl ?: R.drawable.ic_launcher_foreground,
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.LightGray).clickable { 
                                        if (uiState.conversation?.isGroup == true) navController.navigate("group_info/${uiState.conversation?.id}")
                                        else fullscreenImageUrl = uiState.conversation?.profilePictureUrl 
                                    },
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(uiState.conversationTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(uiState.contactPresence, fontSize = 12.sp, color = if (uiState.contactPresence == "Online") Color(0xFF10B981) else Color.Gray)
                                }
                            }
                            
                            Row {
                                IconButton(onClick = onToggleTheme) {
                                    Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, null)
                                }
                                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null) }
                                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Pesquisar") },
                                        leadingIcon = { Icon(Icons.Default.Search, null) },
                                        onClick = { showMenu = false; isSearching = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Notificações") },
                                        leadingIcon = { Icon(Icons.Default.Notifications, null) },
                                        onClick = { showMenu = false; showNotificationSettings = true }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (uiState.conversation?.isMuted == true) "Ativar Som" else "Silenciar") },
                                        leadingIcon = { Icon(if (uiState.conversation?.isMuted == true) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null) },
                                        onClick = { 
                                            showMenu = false
                                            if (conversationId != null) chatViewModel.toggleMute(conversationId)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (uiState.isLastSeenVisible) "Ocultar Visto por Último" else "Mostrar Visto por Último") },
                                        leadingIcon = { Icon(if (uiState.isLastSeenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) },
                                        onClick = { 
                                            showMenu = false
                                            chatViewModel.toggleLastSeen()
                                        }
                                    )
                                    if (uiState.conversation?.isGroup == true) {
                                        DropdownMenuItem(
                                            text = { Text("Dados do Grupo") },
                                            leadingIcon = { Icon(Icons.Default.Info, null) },
                                            onClick = { 
                                                showMenu = false
                                                navController.navigate("group_info/${uiState.conversation?.id}")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- BARRA DE MENSAGEM FIXADA ---
            if (uiState.pinnedMessage != null) {
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mensagem Fixada", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(text = uiState.pinnedMessage?.content ?: "", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { if (conversationId != null) chatViewModel.onPinMessageClick(conversationId, uiState.pinnedMessage!!) }) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // --- MENSAGENS COM AGRUPAMENTO POR DATA ---
            LazyColumn(
                state = listState, 
                modifier = Modifier.weight(1f).fillMaxWidth(), 
                contentPadding = PaddingValues(16.dp), 
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val messages = if (isSearching) uiState.filteredMessages else uiState.messages
                
                val groupedMessages = messages.groupBy { 
                    SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")).format(Date(it.timestamp))
                }

                groupedMessages.forEach { (date, msgs) ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(date, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    items(msgs) { message ->
                        val isMine = message.senderId == currentUserId
                        val isGroup = uiState.conversation?.isGroup == true
                        val senderUser = if (isGroup) uiState.groupMembers[message.senderId] else null
                        MessageBubbleDesign(
                            message = message, 
                            isMine = isMine,
                            isPinned = uiState.pinnedMessage?.id == message.id,
                            onImageClick = { fullscreenImageUrl = it },
                            onPinClick = { if (conversationId != null) chatViewModel.onPinMessageClick(conversationId, it) },
                            audioState = AudioPlaybackState(
                                playingMessageId = playingAudioMessageId,
                                isPlaying = isAudioPlaying,
                                durationMs = audioDurationMs,
                                positionMs = audioPositionMs
                            ),
                            onAudioPlayPause = { msg ->
                                val url = msg.content
                                val isSame = playingAudioMessageId == msg.id
                                if (!isSame) {
                                    playingAudioMessageId = msg.id
                                    audioPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                                    audioPlayer.prepare()
                                    audioPlayer.playWhenReady = true
                                } else {
                                    if (audioPlayer.isPlaying) audioPlayer.pause() else audioPlayer.play()
                                }
                                isAudioPlaying = audioPlayer.isPlaying
                            },
                            onAudioSeek = { newPosMs ->
                                audioPlayer.seekTo(newPosMs)
                                audioPositionMs = newPosMs
                            },
                            senderName = senderUser?.name,
                            senderAvatarUrl = senderUser?.profilePictureUrl,
                            showSender = isGroup
                        )
                    }
                }
            }

            // --- BARRA DE DIGITAÇÃO ---
            Surface(modifier = Modifier.fillMaxWidth().navigationBarsPadding(), tonalElevation = 8.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    if (uiState.isRecording) {
                        RecordingIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showAttachmentMenu = !showAttachmentMenu }) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it; if (conversationId != null) chatViewModel.onTyping(conversationId, it.isNotBlank()) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (uiState.isRecording) "Gravando..." else "Mensagem...") },
                            shape = RoundedCornerShape(24.dp),
                            enabled = !uiState.isRecording
                        )
                        if (messageText.isNotBlank()) {
                            IconButton(onClick = { if (conversationId != null) { chatViewModel.sendMessage(conversationId, messageText); messageText = "" } }) {
                                Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = {
                                if (uiState.isRecording) {
                                    if (conversationId != null) chatViewModel.stopRecording(conversationId)
                                } else {
                                    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                    if (hasMic) chatViewModel.startRecording() else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }) {
                                Icon(
                                    if (uiState.isRecording) Icons.Default.StopCircle else Icons.Default.Mic,
                                    null,
                                    tint = if (uiState.isRecording) Color.Red else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // --- MENU DE ANEXOS ---
            if (showAttachmentMenu) {
                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 16.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceAround) {
                        AttachmentOption(Icons.Default.PhotoLibrary, "Galeria", Color(0xFF9333EA)) { 
                            showAttachmentMenu = false
                            visualMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                        AttachmentOption(Icons.Default.PhotoCamera, "Foto", Color(0xFFEC4899)) { 
                            showAttachmentMenu = false
                            val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCamera) {
                                val uri = createCaptureUri(prefix = "camera_capture", extension = "jpg")
                                pendingPhotoUri = uri
                                cameraPhotoLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        AttachmentOption(Icons.Default.VideoCameraBack, "Vídeo", Color(0xFFF59E0B)) { 
                            showAttachmentMenu = false
                            val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasCamera) {
                                val uri = createCaptureUri(prefix = "video_capture", extension = "mp4")
                                pendingVideoUri = uri
                                cameraVideoLauncher.launch(uri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                        AttachmentOption(Icons.Default.EmojiEmotions, "Sticker", Color(0xFF22C55E)) {
                            showAttachmentMenu = false
                            showStickerPicker = true
                        }
                        AttachmentOption(Icons.Default.InsertDriveFile, "Doc", Color(0xFF3B82F6)) { 
                            showAttachmentMenu = false
                            docLauncher.launch("*/*") 
                        }
                        AttachmentOption(Icons.Default.LocationOn, "Local", Color(0xFF10B981)) { 
                            showAttachmentMenu = false
                            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    }
                }
            }
        }

        if (fullscreenImageUrl != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { fullscreenImageUrl = null }, contentAlignment = Alignment.Center) {
                AsyncImage(model = fullscreenImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }

        if (showStickerPicker) {
            StickerPickerBottomSheet(
                onDismiss = { showStickerPicker = false },
                onStickerSelected = { url ->
                    if (conversationId != null) chatViewModel.sendSticker(conversationId, url)
                    showStickerPicker = false
                }
            )
        }

        if (showNotificationSettings && conversationId != null) {
            NotificationSettingsDialog(
                initialMuted = uiState.conversation?.isMuted ?: false,
                initialHighPriority = uiState.conversation?.isHighPriority ?: false,
                initialVibrationEnabled = uiState.conversation?.vibrationEnabled ?: true,
                onDismiss = { showNotificationSettings = false },
                onSave = { muted, high, vib ->
                    chatViewModel.updateNotificationSettings(conversationId, muted, high, vib)
                    showNotificationSettings = false
                }
            )
        }
    }
}

@Composable
fun AttachmentOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = color.copy(alpha = 0.1f)) {
            Icon(icon, null, tint = color, modifier = Modifier.padding(14.dp))
        }
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPickerBottomSheet(
    onDismiss: () -> Unit,
    onStickerSelected: (String) -> Unit
) {
    val stickers = remember {
        listOf(
            "https://media.giphy.com/media/3oEjI6SIIHBdRxXI40/giphy.gif",
            "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif",
            "https://media.giphy.com/media/5GoVLqeAOo6PK/giphy.gif",
            "https://media.giphy.com/media/ICOgUNjpvO0PC/giphy.gif",
            "https://media.giphy.com/media/3og0IPxMM0erATueVW/giphy.gif",
            "https://media.giphy.com/media/13HgwGsXF0aiGY/giphy.gif",
            "https://media.giphy.com/media/26BRuo6sLetdllPAQ/giphy.gif",
            "https://media.giphy.com/media/3o7aD2saalBwwftBIY/giphy.gif",
            "https://media.giphy.com/media/3o6Zt481isNVuQI1l6/giphy.gif",
            "https://media.giphy.com/media/3ohs4BSacFKI7A717y/giphy.gif"
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                text = "Figurinhas",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(stickers.size) { idx ->
                    val url = stickers[idx]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .size(92.dp)
                            .clickable { onStickerSelected(url) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationSettingsDialog(
    initialMuted: Boolean,
    initialHighPriority: Boolean,
    initialVibrationEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Boolean, Boolean, Boolean) -> Unit
) {
    var muted by remember { mutableStateOf(initialMuted) }
    var highPriority by remember { mutableStateOf(initialHighPriority) }
    var vibrationEnabled by remember { mutableStateOf(initialVibrationEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notificações da conversa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Silenciar")
                        Text("Não mostrar notificações desta conversa", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = muted, onCheckedChange = { muted = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Prioridade alta")
                        Text("Notificação aparece com mais destaque", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = highPriority, onCheckedChange = { highPriority = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vibração")
                        Text("Vibrar ao receber mensagem", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Switch(checked = vibrationEnabled, onCheckedChange = { vibrationEnabled = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(muted, highPriority, vibrationEnabled) }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleDesign(
    message: Message, 
    isMine: Boolean, 
    isPinned: Boolean,
    onImageClick: (String) -> Unit,
    onPinClick: (Message) -> Unit,
    audioState: AudioPlaybackState,
    onAudioPlayPause: (Message) -> Unit,
    onAudioSeek: (Long) -> Unit,
    senderName: String?,
    senderAvatarUrl: String?,
    showSender: Boolean
) {
    val context = LocalContext.current
    var showMessageMenu by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Box {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .combinedClickable(
                        onClick = {
                            when (message.type) {
                                "IMAGE" -> onImageClick(message.content)
                                "VIDEO", "LOCATION" -> {
                                    try {
                                        val uri = Uri.parse(message.content)
                                        val extension = MimeTypeMap.getFileExtensionFromUrl(message.content) ?: ""
                                        val mimeType = if (extension.isNotBlank()) {
                                            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
                                        } else "*/*"
                                        
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, mimeType)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Abrir arquivo com..."))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erro ao abrir arquivo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                "DOCUMENT" -> {
                                    // Documento: tenta abrir pelo mime type; se falhar, usa Google Docs Viewer.
                                    try {
                                        val uri = Uri.parse(message.content)
                                        val extFromName = message.fileName
                                            ?.substringAfterLast('.', missingDelimiterValue = "")
                                            ?.lowercase()
                                            ?.trim()
                                            ?.takeIf { it.isNotBlank() }

                                        val extFromUrl = MimeTypeMap.getFileExtensionFromUrl(message.content)?.lowercase()?.trim()

                                        val ext = extFromName ?: extFromUrl ?: ""

                                        // PDF direto em URL remota costuma falhar no viewer.
                                        // Então: baixamos para cache e abrimos via FileProvider.
                                        if (ext == "pdf") {
                                            val fileName = message.fileName ?: "document.pdf"
                                            coroutineScope.launch {
                                                val downloaded = downloadToCache(context, message.content, fileName)
                                                if (downloaded != null) {
                                                    try {
                                                        val localUri = FileProvider.getUriForFile(
                                                            context,
                                                            "com.example.app_mensagem.fileprovider",
                                                            downloaded
                                                        )
                                                        val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(localUri, "application/pdf")
                                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        }
                                                        withContext(Dispatchers.Main) {
                                                            context.startActivity(Intent.createChooser(pdfIntent, "Abrir PDF com..."))
                                                        }
                                                    } catch (_: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Erro ao abrir PDF baixado.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } else {
                                                    // Se o download falhar (ex: 403/redirect/headers), tenta abrir direto pela URL.
                                                    withContext(Dispatchers.Main) {
                                                        try {
                                                            val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(Uri.parse(message.content), "application/pdf")
                                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                            }
                                                            context.startActivity(Intent.createChooser(pdfIntent, "Abrir PDF com..."))
                                                        } catch (_: Exception) {
                                                            Toast.makeText(context, "Não foi possível baixar ou abrir o PDF.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            val mimeType = when (ext) {
                                                "doc" -> "application/msword"
                                                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                "ppt" -> "application/vnd.ms-powerpoint"
                                                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                                "xls" -> "application/vnd.ms-excel"
                                                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                                "txt" -> "text/plain"
                                                else -> {
                                                    if (ext.isNotBlank()) {
                                                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                                                    } else "*/*"
                                                }
                                            }

                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, mimeType)
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Abrir documento com..."))
                                        }
                                    } catch (_: Exception) {
                                        val extFromName2 = message.fileName
                                            ?.substringAfterLast('.', missingDelimiterValue = "")
                                            ?.lowercase()
                                            ?.trim()

                                        if (extFromName2 == "pdf") {
                                            try {
                                                val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(message.content), "application/pdf")
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(pdfIntent)
                                            } catch (_: Exception) {
                                                // Último fallback: tenta abrir sem mime
                                                try {
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(message.content)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    )
                                                } catch (_: Exception) { }
                                            }
                                        } else {
                                            try {
                                                val encodedUrl = java.net.URLEncoder.encode(message.content, Charsets.UTF_8.name())
                                                val gview = "https://docs.google.com/gview?embedded=true&url=$encodedUrl"
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gview)))
                                            } catch (_: Exception) { }
                                        }
                                    }
                                }
                                "AUDIO" -> onAudioPlayPause(message)
                            }
                        },
                        onLongClick = { showMessageMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (showSender && !senderName.isNullOrBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!isMine) {
                                AsyncImage(
                                    model = senderAvatarUrl ?: R.drawable.ic_launcher_foreground,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = senderName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isMine) {
                                Spacer(modifier = Modifier.width(6.dp))
                                AsyncImage(
                                    model = senderAvatarUrl ?: R.drawable.ic_launcher_foreground,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    if (isPinned) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            Icon(Icons.Default.PushPin, null, modifier = Modifier.size(12.dp), tint = if (isMine) Color.White.copy(0.7f) else Color.Gray)
                            Text(" Fixada", fontSize = 10.sp, color = if (isMine) Color.White.copy(0.7f) else Color.Gray)
                        }
                    }
                    
                    when (message.type) {
                        "IMAGE" -> AsyncImage(model = message.content, contentDescription = null, modifier = Modifier.clip(RoundedCornerShape(8.dp)))
                        "STICKER" -> AsyncImage(model = message.content, contentDescription = null, modifier = Modifier.clip(RoundedCornerShape(8.dp)))
                        "VIDEO" -> {
                            Box(contentAlignment = Alignment.Center) {
                                val request = remember(message.content) {
                                    ImageRequest.Builder(context)
                                        .data(message.content)
                                        .decoderFactory(VideoFrameDecoder.Factory())
                                        .videoFrameMillis(0)
                                        .build()
                                }
                                AsyncImage(
                                    model = request,
                                    contentDescription = null,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(0.3f)),
                                    contentScale = ContentScale.Crop
                                )
                                Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                        }
                        "DOCUMENT" -> {
                            val extFromName = message.fileName
                                ?.substringAfterLast('.', missingDelimiterValue = "")
                                ?.lowercase()
                                ?.trim()
                                ?.takeIf { it.isNotBlank() }

                            val extFromUrl = MimeTypeMap.getFileExtensionFromUrl(message.content)?.lowercase()?.trim()
                            val ext = extFromName ?: extFromUrl ?: ""
                            val isPdf = ext == "pdf"

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, null, tint = if (isMine) Color.White else Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    message.fileName ?: "Documento",
                                    color = if (isMine) Color.White else Color.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isPdf) {
                                    IconButton(
                                        onClick = {
                                            val fileName = message.fileName ?: "document.pdf"
                                            coroutineScope.launch {
                                                val downloaded = downloadToCache(context, message.content, fileName)
                                                if (downloaded != null) {
                                                    try {
                                                        val localUri = FileProvider.getUriForFile(
                                                            context,
                                                            "com.example.app_mensagem.fileprovider",
                                                            downloaded
                                                        )
                                                        val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(localUri, "application/pdf")
                                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        }
                                                        context.startActivity(
                                                            Intent.createChooser(pdfIntent, "Abrir PDF baixado com...")
                                                        )
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "Erro ao abrir PDF baixado.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Não foi possível baixar o PDF.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "Baixar PDF",
                                            tint = if (isMine) Color.White else Color.Black
                                        )
                                    }
                                }
                            }
                        }
                        "AUDIO" -> {
                            val isThisPlaying = audioState.playingMessageId == message.id && audioState.isPlaying
                            val duration = if (audioState.playingMessageId == message.id) audioState.durationMs else 0L
                            val position = if (audioState.playingMessageId == message.id) audioState.positionMs else 0L

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onAudioPlayPause(message) }) {
                                        Icon(
                                            imageVector = if (isThisPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                            contentDescription = null,
                                            tint = if (isMine) Color.White else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Slider(
                                            value = if (duration > 0) (position.coerceIn(0, duration).toFloat() / duration.toFloat()) else 0f,
                                            onValueChange = { frac ->
                                                val newPos = (frac * duration).toLong().coerceAtLeast(0L)
                                                onAudioSeek(newPos)
                                            },
                                            valueRange = 0f..1f
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(formatMs(position), fontSize = 11.sp, color = if (isMine) Color.White.copy(0.8f) else Color.Gray)
                                            Text(formatMs(duration), fontSize = 11.sp, color = if (isMine) Color.White.copy(0.8f) else Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                        "LOCATION" -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Map, null, modifier = Modifier.size(80.dp), tint = if (isMine) Color.White else Color.Gray)
                                Text("📍 Ver Localização", color = if (isMine) Color.White else Color.Blue, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> LinkifiedText(
                            text = message.content,
                            isMine = isMine
                        )
                    }
                    
                    Row(modifier = Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                            fontSize = 10.sp,
                            color = if (isMine) Color.White.copy(0.7f) else Color.Gray
                        )
                        if (isMine) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val (icon, tint) = when {
                                message.readTimestamp > 0L -> Icons.Default.DoneAll to Color(0xFF38BDF8)
                                message.deliveredTimestamp > 0L -> Icons.Default.DoneAll to Color.White.copy(0.7f)
                                else -> Icons.Default.Done to Color.White.copy(0.7f)
                            }
                            Icon(icon, null, modifier = Modifier.size(14.dp), tint = tint)
                        }
                    }
                }
            }

            DropdownMenu(expanded = showMessageMenu, onDismissRequest = { showMessageMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isPinned) "Desafixar" else "Fixar Mensagem") },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) },
                    onClick = { onPinClick(message); showMessageMenu = false }
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = (ms / 1000L).toInt()
    val m = (totalSec / 60).toString().padStart(2, '0')
    val s = (totalSec % 60).toString().padStart(2, '0')
    return "$m:$s"
}

private suspend fun downloadToCache(context: Context, url: String, fileName: String): File? {
    return try {
        val cleanedUrl = url.trim()
        if (cleanedUrl.isBlank()) return null

        // precisa bater com file_paths.xml (cache-path ... path="media/")
        val dir = File(context.cacheDir, "media").apply { mkdirs() }
        val safeName = fileName.ifBlank { "document.pdf" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val outFile = File(dir, safeName)

        withContext(Dispatchers.IO) {
            // Alguns servidores exigem seguir redirect; timeout também ajuda.
            val connection = URL(cleanedUrl).openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                if (this is java.net.HttpURLConnection) {
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                }
            }
            connection.getInputStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (outFile.length() > 0L) outFile else null
    } catch (e: Exception) {
        Log.e("PDF_DOWNLOAD", "Falha ao baixar PDF. url=${url.take(120)} fileName=$fileName", e)
        null
    }
}

@Composable
private fun LinkifiedText(
    text: String,
    isMine: Boolean
) {
    val context = LocalContext.current
    val defaultColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
    val linkColor = if (isMine) Color(0xFF93C5FD) else Color(0xFF2563EB)

    // Regex simples para http/https.
    val urlRegex = remember { Regex("""https?://[^\s]+""") }

    val annotated = remember(text, isMine) {
        buildAnnotatedString {
            var lastIndex = 0
            val matches = urlRegex.findAll(text).toList()
            if (matches.isEmpty()) {
                append(text)
                return@buildAnnotatedString
            }

            matches.forEach { match ->
                val start = match.range.first
                val end = match.range.last + 1

                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
                }

                val url = text.substring(start, end)
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(url)
                }
                pop()

                lastIndex = end
            }

            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(color = defaultColor)
    ) { offset ->
        val annotations = annotated.getStringAnnotations(start = offset, end = offset)
        val url = annotations.firstOrNull { it.tag == "URL" }?.item
        if (!url.isNullOrBlank()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
                Toast.makeText(context, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show()
            }
        }
    }
}