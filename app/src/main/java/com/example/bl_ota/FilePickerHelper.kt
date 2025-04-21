package com.example.bl_ota

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns

object FilePickerHelper {
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
        return name
    }
}
