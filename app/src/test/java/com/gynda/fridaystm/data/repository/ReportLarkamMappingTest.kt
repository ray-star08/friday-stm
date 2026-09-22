package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.model.DocumentKey
import com.google.firebase.firestore.util.CustomClassMapper
import org.junit.Assert.*
import org.junit.Test

/** Real SDK decoding only, without creating a Firebase client or making network queries. */
class ReportLarkamMappingTest {
    @Test fun sdkPreservesLegacyNumericAliasesAndRejectsMalformedStringFields() {
        // The mapper only needs the reference's id; this package-private constructor
        // avoids Firebase component discovery (unavailable in a plain JVM test).
        val constructor = DocumentReference::class.java.getDeclaredConstructor(DocumentKey::class.java, FirebaseFirestore::class.java)
        constructor.isAccessible = true
        val ref = constructor.newInstance(DocumentKey.fromPathString("larkam_records/synthetic"), null)
        val fields = mapOf<String, Any>("userId" to "alice", "distanceInMeters" to 1250L,
            "distanceKm" to 2.5, "timestamp" to "2026-09-18T06:40:00", "imageUrl" to "own.jpg")
        fun decode(data: Map<String, Any>) = CustomClassMapper.convertToCustomClass(data, ReportLarkamDocument::class.java, ref).toRecord()
        val record = decode(fields)
        assertEquals("synthetic", record.id)
        assertEquals("alice", record.userId)
        assertEquals(1250.0, record.distanceMeters, 0.0)
        assertEquals(2.5f, record.distanceKm, 0f)
        assertEquals(900.0, decode(fields + ("distanceMeters" to 900L)).distanceMeters, 0.0)
        for (key in listOf("timestamp", "imageUrl")) {
            assertThrows(RuntimeException::class.java) { decode(fields + (key to mapOf("invalid" to true))) }
        }
    }
}
