package com.nextlevelprogrammers.surakshakawach.data.remote

data class ContactResponse(
    val id: Int,
    val name: String,
    val phone_number: String,
    val email: String?,
    val relationship: String? = null
)