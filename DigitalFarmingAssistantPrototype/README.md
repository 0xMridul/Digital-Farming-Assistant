# Digital Farming Assistant — Complete Android Prototype

This is a self-contained Kotlin + Jetpack Compose prototype covering the requested feature set without requiring API keys.

## Included
- English/Bengali switch
- Local prototype registration/login
- Home dashboard
- Permission-aware device location screen
- Demo weather + forecast + agricultural alert UI
- Crop library with search and details
- Offline crop library state
- Crop diagnosis photo picker/camera intent + demo AI result
- Diagnosis history and offline diagnosis queue
- Farmer community posts, image picker, likes/comments/report UI
- Notifications screen + local notification permission flow
- Profile/settings
- Admin dashboard
- User management prototype
- Content moderation prototype
- Activity/security logs

## Intentionally simulated
Weather data, AI diagnosis, authentication, cloud database, community synchronization, and admin authorization are local demo behavior. No secret keys are required.

## Next production integrations
Supabase Auth/Postgres/Storage, a real weather provider, real AI inference, Room, Firebase Cloud Messaging, server-side admin roles/RLS, and backend moderation.

## Open in Android Studio
1. Extract/open this folder in Android Studio.
2. Let Gradle sync.
3. Use JDK 17 if Android Studio asks for a Gradle JDK; AGP 9.1.1 documents JDK 17.
4. Install Android API 37 if it is not already installed.
5. Run on an Android emulator or physical device.
