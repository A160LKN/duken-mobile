package kz.duken.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kz.duken.mobile.AppContainer
import kz.duken.mobile.DukenApplication

@Composable
fun container(): AppContainer = (LocalContext.current.applicationContext as DukenApplication).container

/** ViewModel с доступом к зависимостям и SavedStateHandle (аргументы маршрута, состояние после гибели процесса) */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer, SavedStateHandle) -> VM): VM {
    val c = container()
    return viewModel(factory = viewModelFactory { initializer { create(c, createSavedStateHandle()) } })
}
