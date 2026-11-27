
package com.example.digitalfarmingassistantv2

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Cloud database settings (Firebase Realtime Database).
 * The first three values come from google-services.json. They are only used when the
 * Google Services Gradle plugin has NOT already started Firebase for you.
 */
object CloudConfig {
    const val PROJECT_ID = "digital-farming-assistance"
    const val APP_ID = "1:1033083873704:android:875313e69d67262d10959d"
    const val API_KEY = "AIzaSyAo_1suehwt-IW4pWUKkNYWFoqmR-IAkOY"

    /**
     * IMPORTANT: copy this from Firebase console > Realtime Database > Data tab
     * (the address shown at the top of the data view, e.g.
     *  https://digital-farming-assistance-default-rtdb.asia-southeast1.firebasedatabase.app).
     * If your database is in the US region it is the .firebaseio.com address below.
     */
    const val DATABASE_URL = "https://digital-farming-assistance-default-rtdb.asia-southeast1.firebasedatabase.app"

    fun isFilled(): Boolean =
        !PROJECT_ID.startsWith("PASTE") && !APP_ID.startsWith("PASTE") && !API_KEY.startsWith("PASTE")
}

fun initFirebase(ctx: Context): Boolean {
    return try {
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId(CloudConfig.PROJECT_ID)
                .setApplicationId(CloudConfig.APP_ID)
                .setApiKey(CloudConfig.API_KEY)
                .build()
            FirebaseApp.initializeApp(ctx, options)
        }
        true
    } catch (e: Exception) {
        false
    }
}
