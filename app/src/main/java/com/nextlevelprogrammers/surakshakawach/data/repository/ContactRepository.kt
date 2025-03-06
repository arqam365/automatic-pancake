package com.nextlevelprogrammers.surakshakawach.repository

import com.nextlevelprogrammers.surakshakawach.data.local.ContactDao
import com.nextlevelprogrammers.surakshakawach.data.remote.ApiService
import com.nextlevelprogrammers.surakshakawach.model.*
import com.nextlevelprogrammers.surakshakawach.uidesign.ContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ContactRepository(
    private val apiService: ApiService,
    private val contactDao: ContactDao
) {

    fun getContactsFromRoom(): Flow<List<ContactInfo>> {
        return contactDao.getAllContacts().map { list -> list.map { it.toContactInfo() } }
    }

    suspend fun saveContactToRoom(contact: ContactInfo) {
        withContext(Dispatchers.IO) {
            val existingContacts = contactDao.getAllContacts().firstOrNull() // ✅ Collect Flow before using `map`
                ?.map { it.toContactInfo() } ?: emptyList()

            if (existingContacts.none { it.phone_number == contact.phone_number }) { // ✅ Prevent duplicate phone numbers
                contactDao.insertContact(contact.toContactEntity())
            } else {
                println("⚠ Contact with phone number ${contact.phone_number} already exists in Room")
            }
        }
    }

    // ✅ API Fetching Logic (Commented for Now)
    suspend fun fetchContactsFromApi(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val contacts = apiService.getContacts(userId).map { it.toContactInfo() }
                contactDao.insertContacts(contacts.map { it.toContactEntity() }) // Store API Data in Room
            } catch (e: Exception) {
                println("❌ API Fetch Failed: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteContactFromApi(userId: String, contactId: String): Boolean {
        return apiService.deleteContact(userId, contactId)
    }

    suspend fun deleteContact(contact: ContactInfo) {
        withContext(Dispatchers.IO) {
            try {
                contactDao.deleteContact(contact.toContactEntity()) // ✅ Remove from Room
                println("✅ Deleted contact from Room DB: ${contact.phone_number}")
            } catch (e: Exception) {
                println("❌ Error deleting contact: ${e.localizedMessage}")
            }
        }
    }

    suspend fun updateContact(contact: ContactInfo) {
        withContext(Dispatchers.IO) {
            contactDao.updateContact(contact.toContactEntity())
        }
    }

    suspend fun addContactToApi(userId: String, contact: ContactInfo) {
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.addContact(userId, contact.toContactRequest())
                println("✅ API Response: $response") // Debug Log
            } catch (e: Exception) {
                println("❌ API Call Failed: ${e.localizedMessage}") // Debug Log
            }
        }
    }
}