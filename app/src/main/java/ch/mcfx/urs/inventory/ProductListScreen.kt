package ch.mcfx.urs.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.InventoryProductDto

// Warning-color tones agreed in  (the broader app-theme redesign is
// still pending, but these two specific colors are already decided and
// don't need to wait for it).
private val FirstWarningColor = Color(0xFFE0813F)
private val SecondWarningColor = Color(0xFFD64545)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    categoryId: String,
    categoryName: String,
    viewModel: ProductsViewModel = viewModel(factory = ProductsViewModel.factory(categoryId, categoryName)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val settingsForm by viewModel.settingsForm.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()

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
                        onLongPress = viewModel::openSettings,
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

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = viewModel::closeSettings) {
            ProductSettingsForm(form = settingsForm, viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductList(
    products: List<InventoryProductDto>,
    onIncrement: (InventoryProductDto) -> Unit,
    onDecrement: (InventoryProductDto) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onLongPress: (InventoryProductDto) -> Unit,
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
            val secondThreshold = product.secondThreshold.toIntOrNull()
            val firstThreshold = product.firstThreshold.toIntOrNull()
            val warningColor = when {
                secondThreshold != null && quantity <= secondThreshold -> SecondWarningColor
                firstThreshold != null && quantity <= firstThreshold -> FirstWarningColor
                else -> null
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(product) }),
                colors = if (warningColor != null) {
                    CardDefaults.cardColors(containerColor = warningColor, contentColor = Color.White)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
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

@Composable
private fun ProductSettingsForm(form: ProductSettingsFormState, viewModel: ProductsViewModel) {
    val product = form.product ?: return
    val thresholdOrderError = stringResource(R.string.inventory_settings_error_threshold_order)
    val reminderFieldsError = stringResource(R.string.inventory_settings_error_reminder_fields)
    val reminderTitle = stringResource(R.string.inventory_settings_reminder_title, product.name)
    val reminderBody = stringResource(R.string.inventory_settings_reminder_body, product.name)

    Column(
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.inventory_settings_title, product.name),
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = form.quantity,
            onValueChange = viewModel::setSettingsQuantity,
            label = { Text(stringResource(R.string.inventory_settings_quantity)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.firstThreshold,
            onValueChange = viewModel::setFirstThreshold,
            label = { Text(stringResource(R.string.inventory_settings_first_threshold)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.secondThreshold,
            onValueChange = viewModel::setSecondThreshold,
            label = { Text(stringResource(R.string.inventory_settings_second_threshold)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.reminderEnabled, onCheckedChange = viewModel::setReminderEnabled)
            Text(stringResource(R.string.inventory_settings_reminder_enable))
        }

        if (form.reminderEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.reminderHour,
                    onValueChange = viewModel::setReminderHour,
                    label = { Text(stringResource(R.string.inventory_settings_reminder_hour)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.reminderMinute,
                    onValueChange = viewModel::setReminderMinute,
                    label = { Text(stringResource(R.string.inventory_settings_reminder_minute)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = form.reminderThreshold,
                onValueChange = viewModel::setReminderThreshold,
                label = { Text(stringResource(R.string.inventory_settings_reminder_threshold)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (form.error != null) {
            Text(form.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                viewModel.submitSettings(
                    thresholdOrderError = thresholdOrderError,
                    reminderFieldsError = reminderFieldsError,
                    reminderTitle = reminderTitle,
                    reminderBody = reminderBody,
                )
            },
            enabled = !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (form.submitting) R.string.saving else R.string.save))
        }
    }
}
