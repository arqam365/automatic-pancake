package com.nextlevelprogrammers.surakshakawach.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.auth.FirebaseAuth
import com.nextlevelprogrammers.surakshakawach.api.Api
import com.nextlevelprogrammers.surakshakawach.api.EmergencyContact
import com.nextlevelprogrammers.surakshakawach.data.ContactDatabase
import com.nextlevelprogrammers.surakshakawach.utils.UserSessionManager
import com.nextlevelprogrammers.surakshakawach.utils.toApiModel
import com.nextlevelprogrammers.surakshakawach.utils.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(onBackPress: () -> Unit) {
    val context = LocalContext.current
    val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid
        ?: UserSessionManager.getSession(context)["userId"]


    if (firebaseUID == null) {
        Toast.makeText(context, "User not logged in", Toast.LENGTH_SHORT).show()
        return
    }

    var emergencyContacts by remember { mutableStateOf(listOf<EmergencyContact>()) }
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Define the pull-to-refresh state
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

// Function to refresh contacts from the server
    val onRefresh: () -> Unit = {
        isRefreshing = true
        coroutineScope.launch {
            loadContacts(firebaseUID, context, coroutineScope) { contacts ->
                emergencyContacts = contacts
                isRefreshing = false
                loading = false
            }
        }
    }

    // Initial load of contacts
    LaunchedEffect(Unit) {
        onRefresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Contacts") },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Emergency Contact")
            }
        }
    ) { innerPadding ->
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { onRefresh() }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(innerPadding)
            ) {
                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        LazyColumn {
                            items(emergencyContacts.size) { index ->
                                val contact = emergencyContacts[index]
                                ContactCard(
                                    contact,
                                    onUpdate = { updatedContact ->
                                        coroutineScope.launch {
                                            try {
                                                // Update local database
                                                val updatedContactEntity = updatedContact.toEntity()
                                                val database = ContactDatabase.getInstance(context)
                                                database.contactDao().updateContact(updatedContactEntity)

                                                // Attempt to sync with the server
                                                if (isNetworkAvailable(context)) {
                                                    val isSuccess = Api().updateEmergencyContacts(
                                                        firebaseUID = firebaseUID,
                                                        oldContacts = listOf(contact),
                                                        newContacts = listOf(updatedContact)
                                                    )
                                                    if (!isSuccess) {
                                                        Toast.makeText(context, "Failed to sync with server. Changes saved locally.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Offline: Changes saved locally.", Toast.LENGTH_SHORT).show()
                                                }
                                                onRefresh()
                                            } catch (e: Exception) {
                                                Log.e("UpdateError", "Error updating contact: ${e.localizedMessage}")
                                            }
                                        }
                                    },
                                    onDelete = { contactToDelete ->
                                        coroutineScope.launch {
                                            try {
                                                // Remove from local database
                                                val database = ContactDatabase.getInstance(context)
                                                val contactEntity = contactToDelete.toEntity()
                                                database.contactDao().deleteContact(contactEntity)

                                                // Attempt to sync with the server
                                                if (isNetworkAvailable(context)) {
                                                    val isSuccess = Api().removeEmergencyContacts(
                                                        firebaseUID = firebaseUID,
                                                        contactDetails = listOf(contactToDelete)
                                                    )
                                                    if (!isSuccess) {
                                                        Toast.makeText(context, "Failed to sync with server. Contact removed locally.", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    Toast.makeText(context, "Offline: Contact removed locally.", Toast.LENGTH_SHORT).show()
                                                }
                                                onRefresh()
                                            } catch (e: Exception) {
                                                Log.e("DeleteError", "Error deleting contact: ${e.localizedMessage}")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        if (isRefreshing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                FloatingActionButton(
                    onClick = { showDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Emergency Contact")
                }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Add Emergency Contact") },
                        text = {
                            Column {
                                TextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Phone Number") })
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    // Check for duplicate contact
                                    val isDuplicate = emergencyContacts.any {
                                        it.email.equals(email, ignoreCase = true) || it.mobile == mobile
                                    }

                                    if (isDuplicate) {
                                        Toast.makeText(
                                            context,
                                            "Contact with this email or phone number already exists",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Button
                                    }

                                    // Proceed to add contact
                                    coroutineScope.launch {
                                        try {
                                            // Save contact to the server
                                            val newContact = EmergencyContact(name, email, mobile)
                                            val isServerSuccess = Api().sendEmergencyContactToServer(
                                                firebaseUID,
                                                newContact.name,
                                                newContact.email,
                                                newContact.mobile
                                            )

                                            if (isServerSuccess) {
                                                // Save contact to local Room database
                                                val database = ContactDatabase.getInstance(context)
                                                database.contactDao().insertContacts(listOf(newContact.toEntity()))

                                                // Update UI and reset dialog state
                                                Toast.makeText(
                                                    context,
                                                    "Contact added successfully",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onRefresh()
                                                name = ""
                                                email = ""
                                                mobile = ""
                                                showDialog = false
                                            } else {
                                                // Handle server failure
                                                Toast.makeText(
                                                    context,
                                                    "Failed to add contact to the server",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            // Log and handle exceptions
                                            Log.e("API_ERROR", "Error: ${e.localizedMessage}")
                                            Toast.makeText(
                                                context,
                                                "Error adding contact: ${e.localizedMessage}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            ) {
                                Text("Add")
                            }
                        },
                        dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
}

private fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
    return networkCapabilities != null &&
            (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
}
// Function to load contacts
private fun loadContacts(
    firebaseUID: String,
    context: Context,
    coroutineScope: CoroutineScope,
    onSuccess: (List<EmergencyContact>) -> Unit
) {
    val database = ContactDatabase.getInstance(context)
    val contactDao = database.contactDao()

    coroutineScope.launch {
        try {
            // Always load local contacts first
            val localContacts = contactDao.getAllContacts()
            withContext(Dispatchers.Main) {
                onSuccess(localContacts.map { it.toApiModel() })
            }

            // Check for network before making API calls
            if (isNetworkAvailable(context)) {
                val userProfileResponse = Api().getUserProfile(firebaseUID)
                if (userProfileResponse != null) {
                    val serverContacts = userProfileResponse.data.emergencyContacts ?: emptyList()

                    // Update local database with server data
                    contactDao.deleteAllContacts()
                    contactDao.insertContacts(serverContacts.map { it.toEntity() })

                    // Update UI with synced data
                    withContext(Dispatchers.Main) {
                        onSuccess(serverContacts)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load contacts from server", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Log.e("NetworkCheck", "No internet connection. Loading local data.")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Offline mode: Showing local contacts", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("API_ERROR", "Error fetching contacts: ${e.localizedMessage}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error fetching contacts", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun ContactCard(contact: EmergencyContact, onUpdate: (EmergencyContact) -> Unit, onDelete: (EmergencyContact) -> Unit) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val maxOffset = 120f
    val animatedOffsetX by animateDpAsState(targetValue = offsetX.dp)
    val scope = rememberCoroutineScope()

    var showEditDialog by remember { mutableStateOf(false) }
    var updatedName by remember { mutableStateOf(contact.name) }
    var updatedEmail by remember { mutableStateOf(contact.email) }
    var updatedMobile by remember { mutableStateOf(contact.mobile) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val dragSensitivity = 1.5f
                        val scaledDragAmount = dragAmount * dragSensitivity
                        val newOffsetX = (offsetX + scaledDragAmount).coerceIn(-maxOffset * 1.2f, 0f)
                        offsetX = if (newOffsetX < -maxOffset) -maxOffset + (newOffsetX + maxOffset) / 3 else newOffsetX
                    },
                    onDragEnd = { if (offsetX > -maxOffset / 2) offsetX = 0f }
                )
            }
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterEnd)
                .padding(horizontal = 12.dp)
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF4CAF50))
                }

                if (showEditDialog) {
                    AlertDialog(
                        onDismissRequest = { showEditDialog = false },
                        title = { Text("Edit Contact") },
                        text = {
                            Column {
                                TextField(value = updatedName, onValueChange = { updatedName = it }, label = { Text("Name") })
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(value = updatedEmail, onValueChange = { updatedEmail = it }, label = { Text("Email") })
                                Spacer(modifier = Modifier.height(8.dp))
                                TextField(value = updatedMobile, onValueChange = { updatedMobile = it }, label = { Text("Mobile") })
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                onUpdate(EmergencyContact(updatedName, updatedEmail, updatedMobile))
                                showEditDialog = false
                            }) { Text("Update") }
                        },
                        dismissButton = { Button(onClick = { showEditDialog = false }) { Text("Cancel") } }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = { scope.launch { onDelete(contact) } }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFF44336))
                }
            }
        }

        Card(
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier
                .offset(x = animatedOffsetX)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(text = "Name: ${contact.name}", fontWeight = FontWeight.Bold)
                Text(text = "Email: ${contact.email}")
                Text(text = "Phone: ${contact.mobile}")
            }
        }
    }
}