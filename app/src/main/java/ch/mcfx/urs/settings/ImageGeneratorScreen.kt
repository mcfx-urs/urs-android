package ch.mcfx.urs.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursFormScrollPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

private val FormErrorColor = Color(0xFFD64545)

@Composable
fun ImageGeneratorScreen(
    viewModel: ImageGeneratorViewModel = viewModel(factory = ImageGeneratorViewModel.Factory),
) {
    val context = LocalContext.current
    val colors = UrsTheme.colors

    val form by viewModel.form.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val generateFailed by viewModel.generateFailed.collectAsStateWithLifecycle()
    val resultPng by viewModel.resultPng.collectAsStateWithLifecycle()
    val savedUri by viewModel.savedUri.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().ursFormScrollPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.image_generator_title), style = UrsTheme.typography.screenTitle)
        UrsText(
            stringResource(R.string.image_generator_description),
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
        )

        UrsTextField(
            value = form.prompt,
            onValueChange = viewModel::setPrompt,
            label = stringResource(R.string.image_generator_prompt_label),
            modifier = Modifier.fillMaxWidth(),
        )

        UrsDropdownField(
            label = stringResource(R.string.image_generator_size_label),
            options = IMAGE_SIZE_OPTIONS,
            selectedLabel = form.size,
            optionLabel = { it },
            onSelect = viewModel::setSize,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsDropdownField(
            label = stringResource(R.string.image_generator_quality_label),
            options = IMAGE_QUALITY_OPTIONS,
            selectedLabel = form.quality,
            optionLabel = { it },
            onSelect = viewModel::setQuality,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsDropdownField(
            label = stringResource(R.string.image_generator_background_label),
            options = IMAGE_BACKGROUND_OPTIONS,
            selectedLabel = form.background,
            optionLabel = { it },
            onSelect = viewModel::setBackground,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsButton(
            text = stringResource(if (generating) R.string.image_generator_generating else R.string.image_generator_generate),
            onClick = viewModel::generate,
            enabled = form.prompt.isNotBlank() && !generating,
            modifier = Modifier.fillMaxWidth(),
        )

        if (generating) {
            UrsProgressIndicator(Modifier.padding(top = Spacing.s))
        }

        if (generateFailed) {
            UrsText(
                stringResource(R.string.image_generator_failed),
                color = FormErrorColor,
                style = UrsTheme.typography.body,
            )
        }

        resultPng?.let { png ->
            AsyncImage(
                model = ImageRequest.Builder(context).data(png).build(),
                imageLoader = (context.applicationContext as UrsApplication).container.imageLoader,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.row)),
            )

            val currentSavedUri = savedUri
            if (currentSavedUri == null) {
                UrsButton(
                    text = stringResource(R.string.image_generator_save),
                    onClick = {
                        val uri = saveGeneratedImage(context, png)
                        if (uri != null) {
                            viewModel.onImageSaved(uri.toString())
                        } else {
                            Toast.makeText(context, R.string.image_generator_save_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                UrsText(
                    stringResource(R.string.image_generator_saved),
                    style = UrsTheme.typography.body,
                    color = colors.onSurfaceMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m), modifier = Modifier.fillMaxWidth()) {
                    UrsOutlinedButton(
                        text = stringResource(R.string.image_generator_share),
                        onClick = { shareGeneratedImage(context, Uri.parse(currentSavedUri)) },
                        modifier = Modifier.weight(1f),
                    )
                    UrsOutlinedButton(
                        text = stringResource(R.string.image_generator_delete),
                        onClick = {
                            deleteGeneratedImage(context, Uri.parse(currentSavedUri))
                            viewModel.onImageDeleted()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Writes [png] into the shared gallery under Pictures/URS. Returns its MediaStore URI, or null on failure. */
private fun saveGeneratedImage(context: Context, png: ByteArray): Uri? = runCatching {
    val resolver = context.contentResolver
    val pending = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "urs-generated-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/URS")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending) ?: return@runCatching null
    val wrote = resolver.openOutputStream(uri)?.use { it.write(png); true } ?: false
    if (!wrote) {
        resolver.delete(uri, null, null)
        return@runCatching null
    }
    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    uri
}.getOrNull()

private fun deleteGeneratedImage(context: Context, uri: Uri) {
    runCatching { context.contentResolver.delete(uri, null, null) }
}

private fun shareGeneratedImage(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
}
