package com.nextlevelprogrammers.surakshakawach.ContactDatabase

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id:Int=0,
    var name:String,
    var number:String
)


@Dao
interface ContactDao {

    @Insert
    suspend fun insertContact(contact: Contact)

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("SELECT * FROM contacts")
    suspend fun getAllContacts(): List<Contact>
}