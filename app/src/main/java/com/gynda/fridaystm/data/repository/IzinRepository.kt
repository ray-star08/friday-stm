package com.gynda.fridaystm.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.gynda.fridaystm.BuildConfig
import com.gynda.fridaystm.data.model.IzinRecord
import com.gynda.fridaystm.util.CloudinaryConfig
import com.gynda.fridaystm.util.FirestoreCollections
import com.gynda.fridaystm.util.StoragePaths
import com.gynda.fridaystm.util.SystemTimeProvider
import com.gynda.fridaystm.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.ZoneId

/**
 * Upload bukti surat pengajuan izin/sakit ke Firebase Storage.
 *
 * Path: `permits/{userId}_{epochMillis}.jpg` — `epochMillis` berasal dari
 * [TimeProvider] yang di-inject (produksi: jam perangkat via
 * [SystemTimeProvider]; test: fake deterministik). Interface agar ViewModel
 * bisa di-unit-test dengan fake tanpa Firebase (SKILL.md §9).
 */
interface IzinProofUploader {
    /**
     * Mengunggah [bytes] (JPEG) milik [userId].
     * @return [Result.success] berisi download URL, atau [Result.failure].
     */
    suspend fun uploadProof(userId: String, bytes: ByteArray): Result<String>
}

/** Firebase Storage-backed [IzinProofUploader]. */
class FirebaseIzinProofUploader(
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) : IzinProofUploader {

    override suspend fun uploadProof(userId: String, bytes: ByteArray): Result<String> =
        runCatching {
            require(userId.isNotBlank()) { "userId must not be blank" }
            require(bytes.isNotEmpty()) { "proof bytes must not be empty" }

            val epochMillis = timeProvider.now()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val ref = storage.reference
                .child(StoragePaths.PERMITS)
                .child("${userId}_${epochMillis}.jpg")

            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        }
}

/** Cloudinary-backed [IzinProofUploader] — SKILL.md policy (permits via Cloudinary). */
class CloudinaryIzinProofUploader(
    private val client: OkHttpClient = OkHttpClient(),
    private val cloudName: String = BuildConfig.CLOUDINARY_CLOUD_NAME,
    private val uploadPreset: String = BuildConfig.CLOUDINARY_UPLOAD_PRESET,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
) : IzinProofUploader {

    override suspend fun uploadProof(userId: String, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(userId.isNotBlank()) { "userId must not be blank" }
                require(bytes.isNotEmpty()) { "proof bytes must not be empty" }
                require(cloudName.isNotBlank() && uploadPreset.isNotBlank()) {
                    "Cloudinary not configured: set cloudinary.cloudName / cloudinary.uploadPreset in local.properties"
                }
                val epochMillis = timeProvider.now()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val publicId = "${userId}_${epochMillis}"
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload_preset", uploadPreset)
                    .addFormDataPart("folder", StoragePaths.PERMITS)
                    .addFormDataPart("public_id", publicId)
                    .addFormDataPart("file", "$publicId.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()
                val request = Request.Builder()
                    .url(CloudinaryConfig.uploadUrl(cloudName))
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("Cloudinary permits upload failed (${response.code}): $payload")
                    }
                    JSONObject(payload).optString("secure_url").ifBlank {
                        error("Cloudinary response missing secure_url: $payload")
                    }
                }
            }
        }
}

/**
 * Menyimpan dokumen pengajuan ke koleksi Firestore `izin_records`.
 *
 * Interface (SKILL.md §9) agar ViewModel di-test dengan fake; write berupa
 * `suspend` + `.await()` → [Result] (SKILL.md §5).
 */
interface IzinRepository {
    /** Menyimpan [record]; `createdAt` di-stamp server. */
    suspend fun submitIzin(record: IzinRecord): Result<Unit>

    /**
     * Approval: update status izin oleh wali kelas.
     * Mengupdate `izin_records/{izinId}` serta alias `izin/{izinId}` bila ada.
     * @param izinId document id
     * @param status APPROVED / REJECTED
     * @param note catatan penolakan / persetujuan (nullable)
     * @param approverUid uid guru yang menyetujui/menolak
     */
    suspend fun updateIzinStatus(
        izinId: String,
        status: com.gynda.fridaystm.util.ApprovalStatus,
        note: String?,
        approverUid: String,
    ): Result<Unit>

    /** Stream daftar izin realtime, filter by kelas & status (ALL = semua). */
    fun observeIzinList(kelas: String, statusFilter: String): kotlinx.coroutines.flow.Flow<List<IzinRecord>>
}

/** Firestore-backed [IzinRepository]. */
class FirebaseIzinRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : IzinRepository {

    override suspend fun submitIzin(record: IzinRecord): Result<Unit> = runCatching {
        val payload = hashMapOf(
            "userId" to record.userId,
            "nama" to record.nama,
            "kelas" to record.kelas,
            "tipe" to record.tipe,
            "alasan" to record.alasan,
            "startDate" to record.startDate,
            "endDate" to record.endDate,
            "proofUrl" to record.proofUrl,
            "status" to record.status,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (record.id.isBlank()) {
            firestore.collection(FirestoreCollections.IZIN_RECORDS)
                .add(payload)
                .await()
        } else {
            firestore.collection(FirestoreCollections.IZIN_RECORDS)
                .document(record.id)
                .set(payload)
                .await()
        }
        Unit
    }

    override suspend fun updateIzinStatus(
        izinId: String,
        status: com.gynda.fridaystm.util.ApprovalStatus,
        note: String?,
        approverUid: String,
    ): Result<Unit> = runCatching {
        require(izinId.isNotBlank()) { "izinId must not be blank" }
        require(approverUid.isNotBlank()) { "approverUid must not be blank" }
        val updates = mutableMapOf<String, Any?>(
            "status" to status.wireValue,
            "approvedByUid" to approverUid,
            "approvalNote" to (note ?: ""),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        // Primary collection `izin_records` (spec extended)
        firestore.collection(FirestoreCollections.IZIN_RECORDS)
            .document(izinId)
            .update(updates)
            .await()
        // Alias `izin` for spec compatibility — best-effort, ignore if missing
        try {
            firestore.collection("izin")
                .document(izinId)
                .update(updates)
                .await()
        } catch (_: Exception) { /* alias optional */ }
        Unit
    }

    override fun observeIzinList(kelas: String, statusFilter: String): kotlinx.coroutines.flow.Flow<List<IzinRecord>> =
        callbackFlow {
            var query: com.google.firebase.firestore.Query = firestore.collection(FirestoreCollections.IZIN_RECORDS)
            if (kelas.isNotBlank() && kelas != "ALL") {
                query = query.whereEqualTo("kelas", kelas)
            }
            if (statusFilter != com.gynda.fridaystm.util.IzinStatus.ALL && statusFilter.isNotBlank()) {
                query = query.whereEqualTo("status", statusFilter)
            }
            query = query.orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            val reg = query.addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.toObjects(IzinRecord::class.java).orEmpty()
                trySend(list)
            }
            awaitClose { reg.remove() }
        }
}
