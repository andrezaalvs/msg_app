# Ajustes do APP de Mensagens (para aluno iniciante)

Este documento lista, de forma bem direta, **o que foi corrigido/implementado** no app com base na lista de funcionalidades que você repassou.  
Para cada item, eu explico: **(1) o problema**, **(2) o que foi feito** e **(3) onde mexer no projeto**.

> Observação importante: nem toda funcionalidade da sua lista foi “completamente concluída do zero” nesta rodada. Quando alguma parte ficou apenas “parcial” ou “dependente de backend”, eu deixo explícito.

---

## 1) Recuperação de senha (clicar e o APP fecha)

**Problema que ocorria:** ao clicar em “Esqueceu a senha?”, a navegação ia para uma rota que não existia no `NavHost`, e isso derrubava o app.

**O que foi feito:** adicionamos a rota `forgot_password` no grafo de navegação para que o `ForgotPasswordScreen` realmente abra.

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/MainActivity.kt` (rota `forgot_password`)

---

## 2) Lupa para buscar contatos (mostra tudo, mas não dá pra digitar)

**Problema que ocorria:** a tela de contatos não tinha campo de busca digitável; só lista.

**O que foi feito:** adicionamos um `OutlinedTextField` “Buscar contatos” e filtramos a lista por:
- `user.name`
- `user.status`

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/presentation/contacts/ContactsScreen.kt`

---

## 3) Notificações personalizadas para grupos ou chats específicos

**Problema que ocorria (do ponto de vista do app):** não existia um painel por conversa para configurar notificação.

**O que foi feito:** implementamos configurações por conversa (chat/grupo) com:
- `isMuted` (silenciar)
- `isHighPriority` (prioridade alta)
- `vibrationEnabled` (vibração)

**Como o Android decide a notificação:** no Android 8+, quem manda é o **NotificationChannel**. Então o `NotificationHelper` agora cria canais diferentes conforme prioridade/vibração.

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/data/model/Conversation.kt` (novos campos)
- `app/src/main/java/com/example/app_mensagem/services/NotificationHelper.kt` (canais por prioridade/vibração)
- `app/src/main/java/com/example/app_mensagem/data/ChatRepository.kt` (usa `isHighPriority`/`vibrationEnabled` ao notificar)
- `app/src/main/java/com/example/app_mensagem/presentation/chat/ChatScreen.kt` (botão/menu “Notificações” + diálogo)
- `app/src/main/java/com/example/app_mensagem/presentation/viewmodel/ChatViewModel.kt` (salvar preferências)

---

## 4) Adicionar/remover contatos

**O que foi feito:** implementamos uma lista de “meus contatos” persistida no Firebase:
- `user-contacts/{meuUid}/{contatoUid} = true`

**Na UI:** na tela de contatos (fora do modo de seleção para grupo), cada usuário agora tem botão:
- **Adicionar** (se não estiver nos seus contatos)
- **Remover** (se estiver)

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/data/ChatRepository.kt`
  - `getMyContactIds()`
  - `addContact()`
  - `removeContact()`
- `app/src/main/java/com/example/app_mensagem/presentation/viewmodel/ContactsViewModel.kt`
  - `myContactIds`
  - `addContact()` / `removeContact()`
- `app/src/main/java/com/example/app_mensagem/presentation/contacts/ContactsScreen.kt`
  - botões e filtro “Todos / Meus contatos”

---

## 5) Importação de contatos do dispositivo

**Você pediu para manter a Opção A:** como o app **não usa telefone**, fizemos matching por **e-mail** (porque no seu `User` do Firebase existe `email`).

**O que foi feito:**
1. Ler contatos do dispositivo para exibir (mantemos a leitura para telefone apenas para UI).
2. Ler e-mails da agenda do dispositivo.
3. Buscar usuários do Firebase (`/users`) e comparar:
   - `user.email` (case-insensitive) com e-mails da agenda
4. Auto-adicionar quem bater em `user-contacts/{meuUid}/...`.

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/data/ChatRepository.kt`
  - `importDeviceContacts()`
  - `importDeviceEmails()`
  - `addContacts()` (batch)
- `app/src/main/java/com/example/app_mensagem/presentation/viewmodel/ContactsViewModel.kt`
  - lógica do matching por e-mail em `importContacts()`
- `app/src/main/java/com/example/app_mensagem/presentation/contacts/ContactsScreen.kt`
  - seção “Contatos do aparelho” + convite

---

## 6) Criar grupos com múltiplos usuários (fecha o app) + Nomear/editar + Adicionar/remover participantes

### 6.1 Criar grupo (corrigir crash/fechamento)
**Problema que ocorria:** ao criar grupo, a navegação ia para uma rota que não existia e/ou o parâmetro (JSON) quebrava a rota.

**O que foi feito:**
- Adicionamos no `NavHost` a rota:
  - `create_group/{memberIdsJson}`
- Na navegação do `ContactsScreen`, codificamos o JSON com `Uri.encode`.
- No destino, decodificamos e convertimos de volta para `List<String>`.

### 6.2 Garantir que o chat do grupo abre imediatamente
**Problema que ocorria em sequência:** às vezes o app navegava pro chat antes do local (Room) ter a conversa, gerando “Conversa não encontrada”.

**O que foi feito:** `createGroup(...)` agora retorna `groupId` e a conversa é inserida no Room antes de navegar.

### 6.3 UI do CreateGroup com feedback
**O que foi feito:**
- mostra “Criando...” durante a operação
- navega para o chat quando finalizar
- mostra Toast em caso de erro

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/MainActivity.kt`
  - rota `forgot_password`
  - rota `create_group/{memberIdsJson}`
- `app/src/main/java/com/example/app_mensagem/presentation/contacts/ContactsScreen.kt`
  - `Uri.encode(...)` no JSON
- `app/src/main/java/com/example/app_mensagem/data/ChatRepository.kt`
  - `createGroup(...)` retorna `groupId` e insere no Room
- `app/src/main/java/com/example/app_mensagem/presentation/viewmodel/ContactsViewModel.kt`
  - navegação para `chat/{groupId}`
- `app/src/main/java/com/example/app_mensagem/presentation/group/CreateGroupScreen.kt`
  - loading + navigation baseada no estado

### 6.4 Nomear/editar e adicionar/remover participantes
A base já existia em `GroupInfoScreen`. Nesta rodada, melhoramos a robustez:
- tratar `groupId` nulo/invalid
- tratar “grupo não encontrado”
- colocar Toast e limpeza de erro
- indicar loading durante ações do grupo

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/presentation/group/GroupInfoScreen.kt`
- `app/src/main/java/com/example/app_mensagem/presentation/viewmodel/GroupInfoViewModel.kt`

---

## 7) Gravar vídeo / tirar foto na hora e enviar arquivo (não dava certo, mas galeria funcionava)

**Problema que ocorria:** o modo “na hora” dependia de `Uri` e permissões; alguns aparelhos falhavam.

**O que foi feito:**
1. **Permissões em runtime**
   - câmera (`Manifest.permission.CAMERA`)
   - microfone (`Manifest.permission.RECORD_AUDIO`)
2. **Criar URI nova para cada captura**
   - evita reutilizar a mesma URI “antiga”
3. Ajustar `file_paths.xml` para liberar `FileProvider` para o local correto.

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/presentation/chat/ChatScreen.kt`
  - launchers de foto/vídeo com `pendingPhotoUri`/`pendingVideoUri`
  - pedir permissões antes de capturar
- `app/src/main/res/xml/file_paths.xml`

---

## 8) Status do usuário (online/offline, ocupado etc.)

**Estado nesta rodada:** o app **já tinha** presença online/offline funcionando no chat (exibido no cabeçalho do chat).  
O que não foi totalmente resolvido aqui é o “status custom” (ex.: “ocupado”) aparecer em lugares coerentes, porque:
- o código atual usa especialmente `isOnline`, `lastSeen` e `lastSeenVisible`
- campos tipo “ocupado” dependem de como vocês atualizam o `users/{uid}/status` no backend e de onde isso é mapeado na UI.

**Onde o status aparece:**
- `app/src/main/java/com/example/app_mensagem/presentation/chat/ChatScreen.kt` (cabeçalho do chat)

---

## 9) Stickers/figurinha (sumiu)

**Problema que ocorria:** não havia mais UI para escolher sticker, então mesmo tendo código de envio, o usuário não conseguia usar.

**O que foi feito:**
- botão “Sticker” no menu de anexos
- uma tela tipo “picker” (bottom sheet) com stickers
- o tipo de mensagem `"STICKER"` agora renderiza imagem na conversa

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/presentation/chat/ChatScreen.kt`

---

## 10) Armazenamento local (offline) + atualizar ao voltar online

**Estado nesta rodada:** existe base com **Room** e sync de conversas/mensagens, mas não foi implementada uma estratégia “fila robusta offline + reconciliação total”.  
Então dá pra dizer que:
- **tem armazenamento local**
- **tem sync**
- mas **não é uma solução completa “WhatsApp offline”**

**Onde mexer (base existente):**
- `app/src/main/java/com/example/app_mensagem/data/local/*`
- `app/src/main/java/com/example/app_mensagem/data/ChatRepository.kt`

---

## 11) Criptografia de mensagens + privacidade

**Estado nesta rodada:** o app já usa `EncryptionUtils` para mensagens de texto e localização (quando do envio/recebimento).  
Porém:
- não garanti criptografia de mídia (foto/vídeo/áudio/documento)
- não foi implementado um painel de privacidade “completo” (controle de dados), além do que já existe nas estruturas atuais.

**Onde mexer (base existente):**
- `app/src/main/java/com/example/app_mensagem/services/EncryptionUtils.kt`
- `app/src/main/java/com/example/app_mensagem/data/ChatRepository.kt`

---

## 12) Microfone: antes gravava, mas parou

**O que foi feito:**
- garantir `RECORD_AUDIO` em runtime
- enviar áudio com `content://` via `FileProvider` (compatível com upload)
- adicionar indicador visual de gravação (“Gravando áudio...”)
- melhorar experiência: player de áudio dentro do chat (sem abrir outro app)

**Onde mexer:**
- `app/src/main/java/com/example/app_mensagem/presentation/viewmodel/ChatViewModel.kt`
  - start/stop recording (envio com `contentUri`)
- `app/src/main/java/com/example/app_mensagem/presentation/chat/ChatScreen.kt`
  - indicador de gravação
  - player in-chat para `"AUDIO"`

---

## 13) Filtro de mensagens por palavra-chave (bugado)

**Estado nesta rodada:** a busca existe via `searchMessages`/`filteredMessages` na tela do chat (bar de pesquisa).  
Porém você relatou que estava bugado originalmente, e **não foi feita uma correção específica nessa lógica nesta rodada** (a gente focou nos crashes e nos recursos de mídia/contatos/notificações).

**Onde mexer (base existente):**
- `app/src/main/java/com/example/app_mensagem/presentation/viewmodel/ChatViewModel.kt`
- `app/src/main/java/com/example/app_mensagem/presentation/chat/ChatScreen.kt`

---

## Bônus: Ajustes para conseguir compilar/exportar APK (ambiente)

Mesmo não sendo parte “funcional” do app, isso impacta o teste.

- Ajustei compatibilidade do AGP para não quebrar no Android Studio:
  - `gradle/libs.versions.toml` (AGP 8.12.2 -> 8.11.1)
- Corrigi dependências de teste no `app/build.gradle.kts` para que o “Build APK” não tente compilar testes e falhar.

**Onde mexer:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

---

## Próximos passos sugeridos (para você saber o que testar)

1. Enviar **vídeo na hora** (não só da galeria).
2. Enviar **áudio** e tocar **dentro do chat**.
3. Abrir **Notificações** e alternar “silenciar / prioridade alta / vibração”.
4. Criar **grupo com múltiplos participantes** e confirmar edição/remover foto/nome.

