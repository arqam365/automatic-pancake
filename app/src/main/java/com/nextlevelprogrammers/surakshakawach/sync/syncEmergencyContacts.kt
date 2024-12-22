package com.nextlevelprogrammers.surakshakawach.sync

import com.nextlevelprogrammers.surakshakawach.api.Api
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.nextlevelprogrammers.surakshakawach.data.ContactDatabase
import com.nextlevelprogrammers.surakshakawach.utils.toApiModel
import com.nextlevelprogrammers.surakshakawach.utils.toEntity

suspend fun syncEmergencyContacts(context: Context) {
    val database = ContactDatabase.getInstance(context)
    val contactDao = database.contactDao()

    try {
        // Fetch server contacts
        val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val serverResponse = Api().getUserProfile(firebaseUID)
        val serverContacts = serverResponse?.data?.emergencyContacts ?: emptyList()

        // Fetch local contacts
        val localContacts = contactDao.getAllContacts()

        // Convert Room entities to API models for comparison
        val localAsApiModel = localContacts.map { it.toApiModel() }

        // Find new contacts to upload
        val newContacts = localAsApiModel.filterNot { serverContacts.contains(it) }

        // Find server contacts to save locally
        val updatedContacts = serverContacts.filterNot { localAsApiModel.contains(it) }

        // Save server contacts locally
        contactDao.deleteAllContacts()
        contactDao.insertContacts(updatedContacts.map { it.toEntity() })

        // Upload new contacts to the server
        if (newContacts.isNotEmpty()) {
            Api().updateEmergencyContacts(
                firebaseUID,
                oldContacts = emptyList(),
                newContacts = newContacts
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}