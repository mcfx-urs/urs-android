package ch.mcfx.urs.data.remote

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * Decodes list responses. The backend encodes an empty result set as the JSON
 * literal `null` instead of `[]`; that literal becomes an empty list, while any
 * other malformed body still throws a `SerializationException`.
 */
class NullAsEmptyListConverterFactory(private val json: Json) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *>? {
        if (type !is ParameterizedType || type.rawType != List::class.java) return null
        val argument = type.actualTypeArguments.singleOrNull() ?: return null
        val element = if (argument is WildcardType) argument.upperBounds.firstOrNull() ?: return null else argument
        val listSerializer = ListSerializer(json.serializersModule.serializer(element))
        return Converter<ResponseBody, List<Any?>> { body ->
            val text = body.use { it.string() }
            if (text.trim() == "null") emptyList() else json.decodeFromString(listSerializer, text)
        }
    }
}
