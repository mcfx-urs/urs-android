package ch.mcfx.urs.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.InventoryCategoryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    onOpenCategory: (InventoryCategoryDto) -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState is CategoriesUiState.Data) {
                FloatingActionButton(onClick = viewModel::openForm) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                CategoriesUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is CategoriesUiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.error_load), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::load) { Text(stringResource(R.string.retry)) }
                }

                is CategoriesUiState.Data -> CategoryList(
                    categories = state.categories,
                    onOpenCategory = onOpenCategory,
                    onDeleteCategory = viewModel::deleteCategory,
                )
            }
        }
    }

    if (showForm) {
        ModalBottomSheet(onDismissRequest = viewModel::closeForm) {
            CategoryForm(form = formState, viewModel = viewModel)
        }
    }
}

@Composable
private fun CategoryList(
    categories: List<InventoryCategoryDto>,
    onOpenCategory: (InventoryCategoryDto) -> Unit,
    onDeleteCategory: (String) -> Unit,
) {
    if (categories.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.inventory_categories_empty),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCategory(category) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        category.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteCategory(category.id) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.inventory_category_remove, category.name),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryForm(form: CategoryFormState, viewModel: CategoriesViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.inventory_category_add), style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = { Text(stringResource(R.string.inventory_category_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.submitFailed) {
            Text(
                stringResource(R.string.error_save),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (form.submitting) R.string.saving else R.string.save))
        }
    }
}
