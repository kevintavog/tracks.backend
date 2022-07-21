package tracks.core.utils

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.ZonedDateTime

object ZonedDateTimeSerializer : KSerializer<ZonedDateTime> {
    override fun serialize(encoder: Encoder, value: ZonedDateTime) {
        encoder.encodeString(DateFormatter.formatISO8601(value))
    }

    override fun deserialize(decoder: Decoder): ZonedDateTime {
        return DateFormatter.parseISO8601(decoder.decodeString())
    }

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ZonedDateTime", PrimitiveKind.STRING)
}
