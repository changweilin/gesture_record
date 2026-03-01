package com.example.gesturerecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.gesturerecord.data.GestureCombination
import com.example.gesturerecord.data.GestureDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val gestureDao: GestureDao) : ViewModel() {

    val combinations: StateFlow<List<GestureCombination>> = gestureDao.getAllCombinations()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCombination(name: String) {
        viewModelScope.launch {
            val currentList = combinations.value
            val nextOrder = (currentList.maxOfOrNull { it.orderIndex } ?: -1) + 1
            gestureDao.insertCombination(GestureCombination(name = name, orderIndex = nextOrder))
        }
    }

    fun deleteCombination(combination: GestureCombination) {
        viewModelScope.launch {
            gestureDao.deleteCombination(combination)
        }
    }

    fun updateCombinationName(combination: GestureCombination, newName: String) {
        viewModelScope.launch {
            gestureDao.updateCombination(combination.copy(name = newName))
        }
    }

    fun duplicateCombination(combination: GestureCombination) {
        viewModelScope.launch {
            val currentList = combinations.value
            val nextOrder = (currentList.maxOfOrNull { it.orderIndex } ?: -1) + 1
            val newCombId = gestureDao.insertCombination(
                GestureCombination(name = "${combination.name} (Copy)", orderIndex = nextOrder)
            )
            val items = gestureDao.getItemsForCombination(combination.id)
            for (item in items) {
                gestureDao.insertItem(item.copy(id = 0, combinationId = newCombId))
            }
        }
    }
}

class MainViewModelFactory(private val gestureDao: GestureDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(gestureDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
