package com.example.fucknotes.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.compose.runtime.Composable

/** Builds a ViewModel that only needs the [Application] handed to it. */
inline fun <reified VM : ViewModel> appViewModelFactory(
    crossinline create: (Application) -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        create(checkNotNull(extras[APPLICATION_KEY])) as T
}

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (Application) -> VM,
): VM = viewModel(key = key, factory = appViewModelFactory(create))
