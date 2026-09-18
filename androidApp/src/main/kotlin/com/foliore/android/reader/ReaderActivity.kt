package com.foliore.android.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.foliore.android.storage.AppLanguagePreferenceRepository

class ReaderActivity : ComponentActivity() {
    private lateinit var reader: ReaderViewModel
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguagePreferenceRepository.localizedContext(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val bookId = intent.getStringExtra(BOOK_ID)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) } ?: run { finish(); return }
        reader = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ReaderViewModel(applicationContext, bookId) as T
        })[ReaderViewModel::class.java]
        setContent { ReaderScreen(reader, onClose = { finish() }) }
    }
    override fun onResume() { super.onResume(); if (::reader.isInitialized) reader.resume() }
    companion object {
        private const val BOOK_ID = "book_id"
        fun intent(context: Context, bookId: String) = Intent(context, ReaderActivity::class.java).putExtra(BOOK_ID, bookId)
    }
}
