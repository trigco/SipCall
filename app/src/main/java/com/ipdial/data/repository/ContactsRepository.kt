package com.ipdial.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import com.ipdial.data.local.AppDatabase
import com.ipdial.data.local.ContactEntity
import com.ipdial.data.model.Contact
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    private val contactDao = AppDatabase.getDatabase(context).contactDao()

    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts().map { entities ->
        entities.map { it.toContact() }
    }

    suspend fun syncContacts() = withContext(Dispatchers.IO) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) 
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return@withContext
        }
        
        val contactsMap = mutableMapOf<String, Contact>()
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.STARRED
            )

            val cursor: Cursor? = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                val starredIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex) ?: "Unknown"
                    val number = it.getString(numberIndex) ?: ""
                    val photoUriStr = it.getString(photoIndex)
                    val isFavorite = it.getInt(starredIndex) == 1

                    val existingContact = contactsMap[id]
                    if (existingContact != null) {
                        if (number.isNotBlank()) {
                            val normalizedNew = number.filter { it.isDigit() }
                            val alreadyHas = existingContact.numbers.any { existingNum -> 
                                existingNum.filter { char -> char.isDigit() } == normalizedNew 
                            }
                            if (!alreadyHas) {
                                contactsMap[id] = existingContact.copy(numbers = existingContact.numbers + number)
                            }
                        }
                    } else if (number.isNotBlank()) {
                        val photoUri = photoUriStr?.toUri()
                        contactsMap[id] = Contact(id, name, listOf(number), photoUri, isFavorite)
                    }
                }
            }
            
            val entities = contactsMap.values.map { ContactEntity.fromContact(it) }
            contactDao.refreshContacts(entities)
            
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepo", "Failed to sync contacts: ${e.message}")
        }
    }

    suspend fun getContacts(query: String? = null): List<Contact> = withContext(Dispatchers.IO) {
        val entities = if (query.isNullOrBlank()) {
            contactDao.getAllContacts().first()
        } else {
            contactDao.searchContacts("%$query%").first()
        }
        entities.map { it.toContact() }
    }

    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        try {
            val values = android.content.ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0)
            }
            context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                values,
                ContactsContract.Contacts._ID + " = ?",
                arrayOf(contactId)
            )
            contactDao.updateFavorite(contactId, isFavorite)
        } catch (e: Exception) {
            android.util.Log.e("ContactsRepo", "Failed to toggle favorite: ${e.message}")
        }
    }
}
