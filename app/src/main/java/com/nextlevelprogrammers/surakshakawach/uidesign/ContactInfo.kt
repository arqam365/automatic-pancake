package com.nextlevelprogrammers.surakshakawach.uidesign

import com.nextlevelprogrammers.surakshakawach.data.local.ContactEntity
import com.nextlevelprogrammers.surakshakawach.model.ContactRequest

data class ContactInfo(
    val name: String,
    val phone_number: String,
    val email: String,
    val relationship: String
)

// Convert ContactEntity -> ContactInfo
fun ContactEntity.toContactInfo(): ContactInfo {
    return ContactInfo(name, phone_number, email, relationship.toString())
}

// Convert ContactInfo -> ContactEntity (for Room Database)
fun ContactInfo.toContactEntity(): ContactEntity {
    return ContactEntity(name = name, phone_number = phone_number, email = email, relationship = relationship)
}