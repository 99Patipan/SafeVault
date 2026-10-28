package app.safevault.data

import app.safevault.crypto.VaultFormatException
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class EntryCodecTest {
    private val sample = EntryContent(
        Categories.BANK, "ธนาคารตัวอย่าง 🏦", "https://example.com", "ผู้ใช้", "a@b.co", "p@ss\nword", "โน้ต\n\nหลายบรรทัด",
    )

    @Test fun roundTrip() = assertEquals(sample, EntryCodec.decode(EntryCodec.encode(sample)))
    @Test fun emptyFieldsRoundTrip() = assertEquals(EntryContent(), EntryCodec.decode(EntryCodec.encode(EntryContent())))

    @Test fun largeNotesRoundTrip() {
        val c = EntryContent(notes = "ก".repeat(50_000))
        assertEquals(c, EntryCodec.decode(EntryCodec.encode(c)))
    }

    @Test fun oversizedFieldRejectedOnEncode() {
        assertThrows(IllegalArgumentException::class.java) {
            EntryCodec.encode(EntryContent(notes = "x".repeat(EntryCodec.MAX_FIELD_BYTES + 1)))
        }
    }

    @Test fun badVersionTruncationAndTrailingRejected() {
        val b = EntryCodec.encode(sample)
        val badVersion = b.clone().also { it[0] = 9 }
        assertThrows(VaultFormatException::class.java) { EntryCodec.decode(badVersion) }
        assertThrows(VaultFormatException::class.java) { EntryCodec.decode(ByteArray(0)) }
        for (n in 1 until b.size) {
            assertThrows("len $n", VaultFormatException::class.java) { EntryCodec.decode(b.copyOf(n)) }
        }
        assertThrows(VaultFormatException::class.java) { EntryCodec.decode(b + byteArrayOf(0)) }
    }

    @Test fun hugeDeclaredLengthRejectedWithoutAllocating() {
        val evil = ByteBuffer.allocate(5).put(1).putInt(Int.MAX_VALUE).array()
        assertThrows(VaultFormatException::class.java) { EntryCodec.decode(evil) }
        val neg = ByteBuffer.allocate(5).put(1).putInt(-1).array()
        assertThrows(VaultFormatException::class.java) { EntryCodec.decode(neg) }
    }

    @Test fun toStringNeverLeaksSecrets() {
        assertFalse(sample.toString().contains("p@ss"))
        assertFalse(VaultEntry(1, 0, 0, sample).toString().contains("p@ss"))
    }
}
