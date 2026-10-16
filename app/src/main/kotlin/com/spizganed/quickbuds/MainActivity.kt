package com.spizganed.quickbuds

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.post {
            LiquidGlassHelper.styleAllCardsInScreen(window.decorView.rootView)
        }
    }
}
