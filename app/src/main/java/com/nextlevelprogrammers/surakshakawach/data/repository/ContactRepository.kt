package com.nextlevelprogrammers.surakshakawach.repository

import com.nextlevelprogrammers.surakshakawach.data.local.ContactDao
import com.nextlevelprogrammers.surakshakawach.data.remote.ApiService
import com.nextlevelprogrammers.surakshakawach.model.*
import com.nextlevelprogrammers.surakshakawach.uidesign.ContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactRepository(
    private val apiService: ApiService,
    private val contactDao: ContactDao
) {

//    suspend fun getContactsFromApi(): List<ContactInfo> {
//        return try {
//            apiService.getContacts().map { it.toContactInfo() }
//        } catch (e: Exception) {
//            emptyList()
//        }
//    }

    suspend fun getContactsFromRoom(): List<ContactInfo> {
        return contactDao.getAllContacts().map { it.toContactInfo() }
    }

    suspend fun saveContactToRoom(contact: ContactInfo) {
        withContext(Dispatchers.IO) {
            try {
                contactDao.insertContact(contact.toContactEntity())
                println("✅ Contact saved to Room: $contact") // Debug Log
            } catch (e: Exception) {
                println("❌ Error saving contact to Room: ${e.localizedMessage}")
            }
        }
    }

    suspend fun deleteContact(contact: ContactInfo) {
        withContext(Dispatchers.IO) {
            contactDao.deleteContact(contact.toContactEntity())
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