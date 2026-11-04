package dev.assay

import android.app.Application
import dev.aarso.crashrecovery.CrashRecovery

class AssayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Local-only crash capture: the report stays in the app's private files until the user
        // shares it from the recovery screen. Nothing is sent anywhere.
        CrashRecovery.install(this, "Assay")
    }
}
