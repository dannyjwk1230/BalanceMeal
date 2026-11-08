package com.example.balancemeal.model.serialization

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object LocalTimeIsoSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_TIME))
    }

    override fun deserialize(decoder: Decoder): LocalTime {
        val value = decoder.decodeString()
        return try {
            LocalTime.parse(value, DateTimeFormatter.ISO_LOCAL_TIME)
        } catch (cause: DateTimeParseException) {
            throw SerializationException("Invalid ISO local time: $value", cause)
        }
    }
}
