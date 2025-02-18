package com.example.surakshakavachui.IntentAction

import com.example.surakshakavachui.uidesign.ContactInfo


sealed interface ContactScreenAction {
    data object OnClickAddContact: ContactScreenAction
    data class OnClickSaveContact(val contact: ContactInfo): ContactScreenAction
    data object OnCancelSaveContact:ContactScreenAction
    data class OnSwipeContactDelete(val contact: ContactInfo): ContactScreenAction
    data class OnSwipeContactEdit(val contact: ContactInfo): ContactScreenAction
    data class OnClickEditSave(val contact: ContactInfo, val newName:String, val newNumber: String): ContactScreenAction
    data object OnClickEditCancel : ContactScreenAction
}