package com.nextlevelprogrammers.surakshakawach.ViewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.nextlevelprogrammers.surakshakawach.IntentAction.ContactScreenAction
import com.nextlevelprogrammers.surakshakawach.repository.ContactRepository
import com.nextlevelprogrammers.surakshakawach.uidesign.ContactInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ContactScreenViewModel(private val contactRepository: ContactRepository) : ViewModel() {
    private val _state = MutableStateFlow(ContactScreenStateValues())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (!userId.isNullOrEmpty()) {
                contactRepository.fetchContactsFromApi(userId) // ✅ Fetch and store API contacts
            }

            contactRepository.getContactsFromRoom().collect { contacts ->
                _state.update { it.copy(contactList = contacts) } // ✅ Auto-update UI
            }
        }
    }


    fun onAction(action: ContactScreenAction) {
        when (action) {
            ContactScreenAction.OnCancelSaveContact -> {
                _state.update { it.copy(showAddDialog = false) }
            }

            ContactScreenAction.OnClickAddContact -> {
                _state.update { it.copy(showAddDialog = true) }
            }

            ContactScreenAction.OnClickEditCancel -> {
                _state.update { it.copy(showEditDialog = false) }
            }

            is ContactScreenAction.OnClickEditSave -> {
                _state.update {
                    it.copy(
                        contactList = it.contactList.map { contact ->
                            if (contact == action.contact) {
                                contact.copy(
                                    name = action.newName,
                                    phone_number = action.newNumber,
                                    email = action.newEmail,
                                    relationship = action.newRelation
                                )
                            } else contact
                        },
                        showEditDialog = false
                    )
                }
            }

            is ContactScreenAction.OnClickSaveContact -> {
                viewModelScope.launch {
                    try {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid
                        if (userId.isNullOrEmpty()) {
                            println("❌ Error: User is not logged in or UID is null!") // Debug Log
                            return@launch
                        }
                        if (userId.isBlank()) {
                            println("❌ Error: User is not logged in!") // Debug Log
                            return@launch
                        }

                        println("🔍 Saving Contact for User ID: $userId") // Debug Log

                        contactRepository.addContactToApi(userId, action.contact) // ✅ Save to API
                        contactRepository.saveContactToRoom(action.contact) // ✅ Save to Room DB

                        _state.update {
                            it.copy(
                                contactList = it.contactList + action.contact,
                                showAddDialog = false
                            )
                        }
                    } catch (e: Exception) {
                        println("❌ Error saving contact: ${e.localizedMessage}") // Debug Log
                        e.printStackTrace()
                    }
                }
            }

            is ContactScreenAction.OnSwipeContactDelete -> {
                viewModelScope.launch {
                    try {
                        val userId = FirebaseAuth.getInstance().currentUser?.uid
                        if (userId.isNullOrEmpty()) {
                            println("❌ User is not logged in!")
                            return@launch
                        }

                        // ✅ Step 1: Delete from Backend API
                        val isDeletedFromAPI = contactRepository.deleteContactFromApi(userId, action.contact.phone_number)

                        if (isDeletedFromAPI) {
                            // ✅ Step 2: Delete from Room DB
                            contactRepository.deleteContact(action.contact)

                            // ✅ Step 3: Update UI State
                            _state.update {
                                it.copy(
                                    contactList = it.contactList.filterNot { contact ->
                                        contact.phone_number == action.contact.phone_number
                                    }
                                )
                            }
                            println("✅ Contact deleted from backend and Room")
                        } else {
                            println("❌ Failed to delete contact from backend")
                        }
                    } catch (e: Exception) {
                        println("❌ Error deleting contact: ${e.localizedMessage}")
                    }
                }
            }

            is ContactScreenAction.OnSwipeContactEdit -> {
                _state.update {
                    it.copy(
                        currentContact = action.contact,
                        showEditDialog = true
                    )
                }
            }
        }
    }
}