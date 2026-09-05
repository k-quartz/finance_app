package com.financialapp.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.financialapp.data.repository.CategoryRepository
import com.financialapp.domain.model.Category
import com.financialapp.domain.model.CategoryTreeNode
import com.financialapp.domain.model.CategoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FlatCategory(val category: Category, val depth: Int)

data class CategoriesUiState(
    val isLoading: Boolean = true,
    val tree: List<CategoryTreeNode> = emptyList(),
    val flat: List<FlatCategory> = emptyList(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { categoryRepository.getTree(includeInactive = false) }
                .onSuccess { tree ->
                    _uiState.update { it.copy(isLoading = false, tree = tree, flat = flatten(tree)) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load categories") }
                }
        }
    }

    fun createCategory(name: String, type: CategoryType, parentId: Long?, onDone: (Boolean, Long?, String?) -> Unit) {
        mutateWithId(onDone, "Unable to create category") {
            categoryRepository.create(Category(name = name.trim(), type = type, parentId = parentId))
        }
    }

    fun updateCategory(categoryId: Long, name: String, type: CategoryType, parentId: Long?, onDone: (Boolean, String?) -> Unit) {
        mutate(onDone, "Unable to update category") {
            val existing = _uiState.value.flat.firstOrNull { it.category.id == categoryId }?.category
                ?: throw IllegalArgumentException("Category does not exist")
            categoryRepository.update(existing.copy(name = name.trim(), type = type, parentId = parentId))
        }
    }

    fun deleteCategory(categoryId: Long, onDone: (Boolean, String?) -> Unit) {
        mutate(onDone, "Unable to delete category") {
            categoryRepository.delete(categoryId)
        }
    }

    private fun mutate(onDone: (Boolean, String?) -> Unit, fallback: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { block() }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess {
                refresh()
                onDone(true, null)
            }.onFailure { error ->
                onDone(false, error.message ?: fallback)
            }
        }
    }

    private fun mutateWithId(onDone: (Boolean, Long?, String?) -> Unit, fallback: String, block: suspend () -> Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val result = runCatching { block() }
            _uiState.update { it.copy(isSaving = false) }
            result.onSuccess { id -> refresh(); onDone(true, id, null) }
                .onFailure { error -> onDone(false, null, error.message ?: fallback) }
        }
    }

    private fun flatten(tree: List<CategoryTreeNode>): List<FlatCategory> {
        val result = mutableListOf<FlatCategory>()
        fun visit(node: CategoryTreeNode, depth: Int) {
            result += FlatCategory(node.category, depth)
            node.children.forEach { visit(it, depth + 1) }
        }
        tree.forEach { visit(it, 0) }
        return result
    }
}
