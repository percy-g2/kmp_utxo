package org.androdevlinux.utxo

import App
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.startup.Initializer

class MainActivity : ComponentActivity() {
    private var backPressedTime = 0L
    private var backToast: Toast? = null
    
    companion object {
        const val EXTRA_COIN_SYMBOL = "coin_symbol"
        const val EXTRA_COIN_DISPLAY_SYMBOL = "coin_display_symbol"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle intent extras for coin detail navigation
        handleCoinDetailIntent(intent)

        setContent {
            App()
        }
    }

    private fun handleBackPress() {
        val currentTime = System.currentTimeMillis()

        if (currentTime - backPressedTime < 2000) {
            // Pressed back twice within 2 seconds, exit the app
            backToast?.cancel()
            finish()
        } else {
            // First back press, show toast
            backPressedTime = currentTime
            backToast?.cancel()
            backToast = Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT)
            backToast?.show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCoinDetailIntent(intent)
    }
    
    private fun handleCoinDetailIntent(intent: Intent?) {
        val symbol = intent?.getStringExtra(EXTRA_COIN_SYMBOL)
        val displaySymbol = intent?.getStringExtra(EXTRA_COIN_DISPLAY_SYMBOL)
        if (symbol != null && displaySymbol != null) {
            // Store in a way that App composable can access
            CoinDetailIntentHandler.setPendingCoinDetail(symbol, displaySymbol)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backToast?.cancel()
    }
    
    override fun onBackPressed() {
        handleBackPress()
    }
}

class AppInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        ContextProvider.setContext(context.applicationContext)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

// CoinDetailIntentHandler and ContextProvider are now in composeApp library
