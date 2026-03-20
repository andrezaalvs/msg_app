package com.example.app_mensagem.presentation.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app_mensagem.MyApplication
import com.example.app_mensagem.data.ChatRepository
import com.example.app_mensagem.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DeviceContact(
    val name: String,
    val phone: String
)

sealed class ContactsUiState {
    object Loading : ContactsUiState()
    data class Success(
        val users: List<User>,
        val deviceContacts: List<DeviceContact> = emptyList(),
        val myContactIds: Set<String> = emptySet()
    ) : ContactsUiState()
    data class Error(val message: String) : ContactsUiState()
}

sealed class ContactNavigationState {
    object Idle : ContactNavigationState()
    data class NavigateToChat(val conversationId: String) : ContactNavigationState()
}

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository
    private var cachedDeviceContacts: List<DeviceContact> = emptyList()
    private var cachedMyContactIds: Set<String> = emptySet()

    private val _uiState = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    val uiState: StateFlow<ContactsUiState> = _uiState

    private val _navigationState = MutableStateFlow<ContactNavigationState>(ContactNavigationState.Idle)
    val navigationState: StateFlow<ContactNavigationState> = _navigationState

    init {
        val db = (application as MyApplication).database
        repository = ChatRepository(db.conversationDao(), db.messageDao(), application)
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = ContactsUiState.Loading
            try {
                val users = repository.getUsers()
                cachedMyContactIds = repository.getMyContactIds()
                _uiState.value = ContactsUiState.Success(users, cachedDeviceContacts, cachedMyContactIds)
            } catch (e: Exception) {
                _uiState.value = ContactsUiState.Error(e.message ?: "Falha ao carregar usuários.")
            }
        }
    }

    fun importContacts() {
        viewModelScope.launch {
            _uiState.value = ContactsUiState.Loading
            try {
                // Lê contatos (telefone) para exibir + lê e-mails para fazer matching
                val deviceContacts = repository.importDeviceContacts()
                    .map { (name, phone) -> DeviceContact(name = name, phone = phone) }
                    .distinctBy { it.phone }
                    .sortedBy { it.name.lowercase() }

                cachedDeviceContacts = deviceContacts
                val deviceEmails = repository.importDeviceEmails()
                val users = repository.getUsers()
                cachedMyContactIds = repository.getMyContactIds()

                // Matching por e-mail (case-insensitive). Adiciona automaticamente em "Meus contatos".
                val toAdd = users
                    .filter { it.email.isNotBlank() && it.email.trim().lowercase() in deviceEmails }
                    .map { it.uid }
                    .filter { it !in cachedMyContactIds }
                    .distinct()

                if (toAdd.isNotEmpty()) {
                    repository.addContacts(toAdd)
                    cachedMyContactIds = cachedMyContactIds + toAdd
                }

                _uiState.value = ContactsUiState.Success(users, deviceContacts, cachedMyContactIds)

                Toast.makeText(
                    getApplication(),
                    "${deviceContacts.size} contatos lidos. ${toAdd.size} adicionados automaticamente (por e-mail).",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                _uiState.value = ContactsUiState.Error("Erro ao acessar agenda: ${e.message}")
            }
        }
    }

    fun addContact(userId: String) {
        viewModelScope.launch {
            try {
                repository.addContact(userId)
                cachedMyContactIds = cachedMyContactIds + userId
                val current = _uiState.value
                if (current is ContactsUiState.Success) {
                    _uiState.value = current.copy(myContactIds = cachedMyContactIds)
                }
            } catch (e: Exception) {
                _uiState.value = ContactsUiState.Error(e.message ?: "Falha ao adicionar contato")
            }
        }
    }

    fun removeContact(userId: String) {
        viewModelScope.launch {
            try {
                repository.removeContact(userId)
                cachedMyContactIds = cachedMyContactIds - userId
                val current = _uiState.value
                if (current is ContactsUiState.Success) {
                    _uiState.value = current.copy(myContactIds = cachedMyContactIds)
                }
            } catch (e: Exception) {
                _uiState.value = ContactsUiState.Error(e.message ?: "Falha ao remover contato")
            }
        }
    }

    fun onUserClicked(user: User) {
        viewModelScope.launch {
            try {
                val conversationId = repository.createOrGetConversation(user)
                _navigationState.value = ContactNavigationState.NavigateToChat(conversationId)
            } catch (e: Exception) {
                _uiState.value = ContactsUiState.Error(e.message ?: "Falha ao criar conversa")
            }
        }
    }

    fun createGroup(name: String, memberIds: List<String>) {
        viewModelScope.launch {
            try {
                _uiState.value = ContactsUiState.Loading
                val groupId = repository.createGroup(name, memberIds)
                // Recarrega listas e navega direto para o chat do grupo
                val users = repository.getUsers()
                cachedMyContactIds = repository.getMyContactIds()
                _uiState.value = ContactsUiState.Success(users, cachedDeviceContacts, cachedMyContactIds)
                _navigationState.value = ContactNavigationState.NavigateToChat(groupId)
            } catch (e: Exception) {
                _uiState.value = ContactsUiState.Error(e.message ?: "Falha ao criar grupo")
            }
        }
    }

    fun onNavigated() {
        _navigationState.value = ContactNavigationState.Idle
    }
}