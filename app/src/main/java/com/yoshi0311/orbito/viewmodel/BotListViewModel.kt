package com.yoshi0311.orbito.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yoshi0311.orbito.model.BotConfig
import com.yoshi0311.orbito.model.BotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BotListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BotRepository(application.filesDir)

    private val _userBots = MutableStateFlow<List<BotConfig>>(emptyList())
    val userBots: StateFlow<List<BotConfig>> = _userBots.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _userBots.value = repository.userBots()
        }
    }
}
