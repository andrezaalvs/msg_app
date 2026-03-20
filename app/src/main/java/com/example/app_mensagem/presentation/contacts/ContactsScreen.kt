package com.example.app_mensagem.presentation.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.app_mensagem.R
import com.example.app_mensagem.data.model.User
import com.example.app_mensagem.presentation.viewmodel.ContactNavigationState
import com.example.app_mensagem.presentation.viewmodel.ContactsUiState
import com.example.app_mensagem.presentation.viewmodel.ContactsViewModel
import com.example.app_mensagem.presentation.viewmodel.DeviceContact
import com.google.gson.Gson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    navController: NavController,
    contactsViewModel: ContactsViewModel = viewModel(),
    selectionMode: Boolean = false
) {
    val contactsState by contactsViewModel.uiState.collectAsState()
    val navigationState by contactsViewModel.navigationState.collectAsState()
    val selectedUsers = remember { mutableStateListOf<User>() }
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyMyContacts by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactsViewModel.importContacts()
        }
    }

    LaunchedEffect(navigationState) {
        if (navigationState is ContactNavigationState.NavigateToChat) {
            val conversationId = (navigationState as ContactNavigationState.NavigateToChat).conversationId
            navController.navigate("chat/$conversationId") {
                popUpTo("home")
            }
            contactsViewModel.onNavigated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectionMode) "Selecionar Participante" else "Contatos") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedUsers.isNotEmpty() && !selectionMode) {
                FloatingActionButton(onClick = {
                    if (selectedUsers.size == 1) {
                        contactsViewModel.onUserClicked(selectedUsers.first())
                    } else {
                        val userIdsJson = Gson().toJson(selectedUsers.map { it.uid })
                        navController.navigate("create_group/${Uri.encode(userIdsJson)}")
                    }
                }) {
                    Icon(Icons.Default.Check, contentDescription = "Confirmar")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = contactsState) {
                is ContactsUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ContactsUiState.Success -> {
                    val filteredUsers = remember(state.users, state.myContactIds, searchQuery, showOnlyMyContacts) {
                        val q = searchQuery.trim()
                        val base = if (showOnlyMyContacts) {
                            state.users.filter { it.uid in state.myContactIds }
                        } else {
                            state.users
                        }
                        if (q.isBlank()) return@remember base
                        val qLower = q.lowercase()
                        base.filter { user ->
                            user.name.lowercase().contains(qLower) ||
                                user.status.lowercase().contains(qLower)
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            label = { Text("Buscar contatos") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )

                        if (!selectionMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = !showOnlyMyContacts,
                                    onClick = { showOnlyMyContacts = false },
                                    label = { Text("Todos") }
                                )
                                FilterChip(
                                    selected = showOnlyMyContacts,
                                    onClick = { showOnlyMyContacts = true },
                                    label = { Text("Meus contatos") }
                                )
                            }
                        }
                        if (!selectionMode) {
                            TextButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                        contactsViewModel.importContacts()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            ) {
                                Text("Importar da Agenda")
                            }
                            HorizontalDivider()
                        }

                        if (!selectionMode && state.deviceContacts.isNotEmpty()) {
                            DeviceContactsSection(
                                contacts = state.deviceContacts,
                                onInvite = { contact ->
                                    val shareText = "Oi ${contact.name}! Baixa o nosso app de mensagens 😊"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Convidar contato"))
                                    } catch (_: Exception) { }
                                }
                            )
                            HorizontalDivider()
                        }
                        
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredUsers) { user ->
                                val isSelected = selectedUsers.any { it.uid == user.uid }
                                val isInMyContacts = user.uid in state.myContactIds
                                UserItem(
                                    user = user,
                                    isSelected = isSelected,
                                    showAddRemove = !selectionMode,
                                    isInMyContacts = isInMyContacts,
                                    onAddRemoveClick = {
                                        if (isInMyContacts) contactsViewModel.removeContact(user.uid)
                                        else contactsViewModel.addContact(user.uid)
                                    },
                                    onClick = {
                                        if (selectionMode) {
                                            // Se for modo de seleção (adicionando participante), volta com o ID
                                            navController.previousBackStackEntry
                                                ?.savedStateHandle
                                                ?.set("selectedUserId", user.uid)
                                            navController.popBackStack()
                                        } else {
                                            if (isSelected) {
                                                selectedUsers.removeAll { it.uid == user.uid }
                                            } else {
                                                selectedUsers.add(user)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                is ContactsUiState.Error -> {
                    Text(text = "Erro: ${state.message}", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
fun UserItem(
    user: User,
    isSelected: Boolean,
    showAddRemove: Boolean = false,
    isInMyContacts: Boolean = false,
    onAddRemoveClick: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.profilePictureUrl ?: R.drawable.ic_launcher_foreground,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, fontWeight = FontWeight.Bold)
            Text(user.status, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        if (showAddRemove) {
            IconButton(onClick = onAddRemoveClick) {
                Icon(
                    imageVector = if (isInMyContacts) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                    contentDescription = if (isInMyContacts) "Remover contato" else "Adicionar contato"
                )
            }
        }
    }
}

@Composable
private fun DeviceContactsSection(
    contacts: List<DeviceContact>,
    onInvite: (DeviceContact) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("Contatos do aparelho", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(contacts.take(30)) { c ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(c.name.ifBlank { "Sem nome" }, fontWeight = FontWeight.SemiBold)
                        Text(c.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    TextButton(onClick = { onInvite(c) }) { Text("Convidar") }
                }
            }
            if (contacts.size > 30) {
                item {
                    Text(
                        text = "Mostrando 30 de ${contacts.size} contatos",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
