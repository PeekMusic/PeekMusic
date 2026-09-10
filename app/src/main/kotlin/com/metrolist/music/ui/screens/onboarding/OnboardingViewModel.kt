/*
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.HasCompletedOnboardingKey
import com.metrolist.music.utils.safeDataStoreEdit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    fun setPage(page: Int) {
        _currentPage.value = page
    }

    /**
     * Marks onboarding as completed and either navigates to login or dismisses the flow.
     * The flag is written before navigating to login because the login flow restarts the app.
     */
    fun complete(
        login: Boolean,
        onLogin: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            context.safeDataStoreEdit { settings ->
                settings[HasCompletedOnboardingKey] = true
            }
            withContext(Dispatchers.Main) {
                if (login) onLogin() else onDismiss()
            }
        }
    }
}
