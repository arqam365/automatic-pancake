package com.example.surakshakavachui.ViewModels

import com.example.surakshakavachui.uidesign.ContactInfo

data class ContactScreenStateValues(
    var showAddDialog: Boolean =false,
    var showEditDialog: Boolean =false,
    var currentContact: ContactInfo? = null,
    var contactList: List<ContactInfo> = emptyList(),
)
