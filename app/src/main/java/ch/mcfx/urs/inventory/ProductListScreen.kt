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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.InventoryProductDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    categoryId: String,
    categoryName: String,
    viewModel: ProductsViewModel = viewModel(factory = ProductsViewModel.factory(categoryId)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState is ProductsUiState.Data) {
                FloatingActionButton(onClick = viewModel::openForm) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    categoryName,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                when (val state = uiState) {
                    ProductsUiState.Loading -> Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                    is ProductsUiState.Error -> Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.error_load), style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = viewModel::load) { Text(stringResource(R.string.retry)) }
                        }
                    }

                    is ProductsUiState.Data -> ProductList(
                        products = state.products,
                        onIncrement = viewModel::increment,
                        onDecrement = viewModel::decrement,
                        onDeleteProduct = viewModel::deleteProduct,
                    )
                }
            }
        }
    }

    if (showForm) {
        ModalBottomSheet(onDismissRequest = viewModel::closeForm) {
            ProductForm(form = formState, viewModel = viewModel)
        }
    }
}

@Composable
private fun ProductList(
    products: List<InventoryProductDto>,
    onIncrement: (InventoryProductDto) -> Unit,
    onDecrement: (InventoryProductDto) -> Unit,
    onDeleteProduct: (String) -> Unit,
) {
    if (products.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.inventory_products_empty),
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
        items(products, key = { it.id }) { product ->
            val quantity = product.quantity.toIntOrNull() ?: 0
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDecrement(product) }, enabled = quantity > 0) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.inventory_product_decrement))
                    }
                    Text(
                        quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { onIncrement(product) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.inventory_product_increment))
                    }
                    IconButton(onClick = { onDeleteProduct(product.id) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.inventory_product_remove, product.name),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductForm(form: ProductFormState, viewModel: ProductsViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.inventory_product_add), style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = { Text(stringResource(R.string.inventory_product_name)) },
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
