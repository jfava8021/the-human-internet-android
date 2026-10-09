package com.thehumaninternet.app.supabase

import com.thehumaninternet.app.models.VerifiedPhoto
import com.thehumaninternet.app.models.databaseJson
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import kotlin.time.Duration.Companion.seconds

object SupabaseConfig {
    const val URL = "https://xpjkgngifffzdaikjakw.supabase.co"

    // Publishable key — safe to embed in client code, RLS enforces access control.
    const val PUBLISHABLE_KEY = "sb_publishable_tmrKLSHCT0-ZeCFTsPxQEg_Qijh19mI"

    /**
     * Where the browser hands control back after Sign in with Google:
     * `thehumaninternet://login-callback`. Must be listed under Supabase
     * Auth's redirect URLs, and matches the intent filter in the manifest.
     */
    const val AUTH_CALLBACK_SCHEME = VerifiedPhoto.DEEP_LINK_SCHEME
    const val AUTH_CALLBACK_HOST = "login-callback"
}

/** The same Supabase project the iOS app and the website talk to. */
val supabase = createSupabaseClient(SupabaseConfig.URL, SupabaseConfig.PUBLISHABLE_KEY) {
    defaultSerializer = KotlinXSerializer(databaseJson)
    // The library default is 10s, which a full-resolution photo going through
    // the signing Lambda can exceed on a slow connection.
    requestTimeout = 60.seconds

    install(Auth) {
        scheme = SupabaseConfig.AUTH_CALLBACK_SCHEME
        host = SupabaseConfig.AUTH_CALLBACK_HOST
        flowType = FlowType.PKCE
    }
    install(Postgrest)
    install(Storage)
    install(Functions)
}
