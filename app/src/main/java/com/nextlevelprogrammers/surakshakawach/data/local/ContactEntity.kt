package com.nextlevelprogrammers.surakshakawach.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone_number: String,
    val email: String,
    val relationship: String
)