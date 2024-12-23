package com.nextlevelprogrammers.surakshakawach.sync

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.nextlevelprogrammers.surakshakawach.api.Api
import com.google.firebase.auth.FirebaseAuth
import com.nextlevelprogrammers.surakshakawach.data.ContactDatabase
import com.nextlevelprogrammers.surakshakawach.data.EmergencyContactEntity
import kotlinx.coroutines.runBlocking

class SyncWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        val database = ContactDatabase.getInstance(applicationContext)
        val contactDao = database.contactDao()

        return try {
            val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.failure()

            // Fetch contacts from the server
            val serverContacts = runBlocking {
                Api().getUserProfile(firebaseUID)?.data?.emergencyContacts?.map {
                    EmergencyContactEntity(name = it.name, email = it.email, mobile = it.mobile)
                } ?: emptyList()
            }

            // Sync local and server contacts
            runBlocking {
                contactDao.deleteAllContacts()
                contactDao.insertContacts(serverContacts)
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}