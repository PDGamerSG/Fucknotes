package com.example.fucknotes

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Runs isolated database tests without starting the owner's shelf or restoring their tabs. */
class DatabaseTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
