package com.nextlevelprogrammers.surakshakawach.ViewModels

import androidx.lifecycle.ViewModel
import com.nextlevelprogrammers.surakshakawach.IntentAction.ContactScreenAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ContactScreenViewModel: ViewModel() {
    private val _state= MutableStateFlow(ContactScreenStateValues())
    val state= _state.asStateFlow()

    fun onAction(action: ContactScreenAction){
        when(action){
            ContactScreenAction.OnCancelSaveContact -> {
                _state.update { it.copy(
                    showAddDialog = false
                ) }
            }
            ContactScreenAction.OnClickAddContact -> {
                _state.update { it.copy(
                    showAddDialog = true
                ) }
            }
            ContactScreenAction.OnClickEditCancel -> {
                _state.update { it.copy(
                    showEditDialog = false
                ) }
            }
            is ContactScreenAction.OnClickEditSave -> {
                _state.update { it.copy(
                    contactList = state.value.contactList.map {
                        if(it==action.contact) it.copy(name = action.newName, number = action.newNumber, email = action.newEmail) else it
                    },
                    showEditDialog = false
                )
                }
            }
            is ContactScreenAction.OnClickSaveContact ->{
                _state.update { it.copy(
                    contactList = state.value.contactList+ action.contact,
                    showAddDialog = false
                ) }
            }
            is ContactScreenAction.OnSwipeContactDelete -> {
                _state.update { it.copy(
                    contactList = state.value.contactList-action.contact
                ) }
            }
            is ContactScreenAction.OnSwipeContactEdit -> {
                _state.update { it.copy(
                    currentContact = action.contact,
                    showEditDialog = true
                ) }
            }
        }
    }

}