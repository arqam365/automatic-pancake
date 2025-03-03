package com.nextlevelprogrammers.surakshakawach.model

import com.nextlevelprogrammers.surakshakawach.data.local.ContactEntity
import com.nextlevelprogrammers.surakshakawach.uidesign.ContactInfo

// Convert Room Database Entity to ContactInfo
fun ContactEntity.toContactInfo(): ContactInfo {
    return ContactInfo(
        name = this.name,
        phone_number = this.phone_number,
        email = this.email,
        relationship = this.relationship
    )
}

// Convert API Response to ContactInfo
fun ContactResponse.toContactInfo(): ContactInfo {
    return ContactInfo(
        name = this.name,
        phone_number = this.phone_number,
        email = this.email ?: "",
        relationship = this.relationship.toString()
    )
}

// Convert ContactInfo to Room Entity
fun ContactInfo.toContactEntity(): ContactEntity {
    return ContactEntity(
        name = this.name,
        phone_number = this.phone_number,
        email = this.email,
        relationship = this.relationship
    )
}

// Convert ContactInfo to API Request
fun ContactInfo.toContactRequest(): ContactRequest {
    return ContactRequest(
        name = this.name,
        phone_number = this.phone_number,
        email = this.email,
        relationship = this.relationship
    )
}