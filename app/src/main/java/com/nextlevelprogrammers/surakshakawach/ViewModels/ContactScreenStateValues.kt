package com.nextlevelprogrammers.surakshakawach.ViewModels

import com.nextlevelprogrammers.surakshakawach.uidesign.ContactInfo

data class ContactScreenStateValues(
    var showAddDialog: Boolean =false,
    var showEditDialog: Boolean =false,
    var currentContact: ContactInfo? = null,
    var contactList: List<ContactInfo> = emptyList(),
)
