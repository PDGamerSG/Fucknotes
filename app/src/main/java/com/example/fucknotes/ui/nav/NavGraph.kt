package com.example.fucknotes.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.fucknotes.ui.appViewModel
import com.example.fucknotes.ui.editor.EditorScreen
import com.example.fucknotes.ui.editor.EditorViewModel
import com.example.fucknotes.ui.home.HomeScreen
import com.example.fucknotes.ui.home.HomeViewModel
import com.example.fucknotes.ui.search.SearchScreen
import com.example.fucknotes.ui.search.SearchViewModel
import com.example.fucknotes.ui.settings.SettingsScreen
import com.example.fucknotes.ui.settings.SettingsViewModel
import com.example.fucknotes.ui.trash.TrashScreen
import com.example.fucknotes.ui.trash.TrashViewModel

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{noteId}"
    const val SEARCH = "search"
    const val TRASH = "trash"
    const val SETTINGS = "settings"

    fun editor(noteId: Long) = "editor/$noteId"
}

@Composable
fun FucknotesNavGraph(
    navController: NavHostController,
    openNoteId: Long?,
    onOpenNoteHandled: () -> Unit,
) {
    // A reminder notification hands us a note id; jump straight into it once.
    LaunchedEffect(openNoteId) {
        if (openNoteId != null && openNoteId > 0) {
            navController.navigate(Routes.editor(openNoteId))
            onOpenNoteHandled()
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            val viewModel = appViewModel { HomeViewModel(it) }
            HomeScreen(
                viewModel = viewModel,
                onOpenNote = { navController.navigate(Routes.editor(it)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenTrash = { navController.navigate(Routes.TRASH) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) { entry ->
            val noteId = entry.arguments?.getLong("noteId") ?: return@composable
            val viewModel = appViewModel(key = "editor-$noteId") { EditorViewModel(it, noteId) }
            EditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenNote = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.editor(id))
                },
            )
        }

        composable(Routes.SEARCH) {
            val viewModel = appViewModel { SearchViewModel(it) }
            SearchScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOpenNote = { navController.navigate(Routes.editor(it)) },
            )
        }

        composable(Routes.TRASH) {
            val viewModel = appViewModel { TrashViewModel(it) }
            TrashScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            val viewModel = appViewModel { SettingsViewModel(it) }
            SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
    }
}
