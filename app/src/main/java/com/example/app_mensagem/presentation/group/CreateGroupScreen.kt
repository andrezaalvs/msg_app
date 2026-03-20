package com.example.app_mensagem.presentation.group

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.app_mensagem.presentation.viewmodel.ContactsViewModel
import com.example.app_mensagem.presentation.viewmodel.ContactNavigationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    navController: NavController,
    memberIds: List<String>,
    contactsViewModel: ContactsViewModel = viewModel()
) {
    var groupName by remember { mutableStateOf("") }
    val navigationState by contactsViewModel.navigationState.collectAsState()
    val contactsState by contactsViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(navigationState) {
        if (navigationState is ContactNavigationState.NavigateToChat) {
            val conversationId = (navigationState as ContactNavigationState.NavigateToChat).conversationId
            navController.navigate("chat/$conversationId") {
                popUpTo("home")
            }
            contactsViewModel.onNavigated()
        }
    }

    LaunchedEffect(contactsState) {
        if (contactsState is com.example.app_mensagem.presentation.viewmodel.ContactsUiState.Error) {
            Toast.makeText(
                context,
                (contactsState as com.example.app_mensagem.presentation.viewmodel.ContactsUiState.Error).message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novo Grupo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Nome do Grupo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    contactsViewModel.createGroup(groupName, memberIds)
                },
                enabled = groupName.isNotBlank() && contactsState !is com.example.app_mensagem.presentation.viewmodel.ContactsUiState.Loading
            ) {
                if (contactsState is com.example.app_mensagem.presentation.viewmodel.ContactsUiState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Criando...")
                } else {
                    Text("Concluir")
                }
            }
        }
    }
}