package dev.nocny.lessive

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.nocny.lessive.nfc.NfcHub
import dev.nocny.lessive.ui.LessiveRoot
import dev.nocny.lessive.ui.theme.LessiveTheme

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {
    private val adapter by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { LessiveTheme { LessiveRoot() } }
    }

    override fun onResume() {
        super.onResume()
        val nfc = adapter
        NfcHub.setAvailability(
            when {
                nfc == null -> NfcHub.Availability.Missing
                !nfc.isEnabled -> NfcHub.Availability.Disabled
                else -> NfcHub.Availability.Ready
            }
        )
        // Reader mode, not foreground dispatch: the system must not probe the tag for NDEF
        // or play sounds while we hold the session for the GPO handshake.
        nfc?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1500) },
        )
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) = NfcHub.onTagDiscovered(tag)
}
