package com.kgcaudit.olocycle.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 로컬 백업 파일의 인코딩·암호화. 인터넷으로 나가지 않고, 사용자가 정한 암호로만 열 수 있게
 * **AES-256-GCM**(기밀성+무결성)으로 봉인한다. 키는 암호에서 **PBKDF2(HMAC-SHA256)**로 유도하며,
 * 매 백업마다 새 소금(salt)·IV를 쓴다. 비밀번호가 틀리면 복호화가 인증 실패로 예외를 던진다(임의 파싱 없음).
 *
 * 파일 구조(바이너리): MAGIC("OLOB",4) · 버전(1) · salt(16) · iv(12) · 암호문+태그.
 * 평문은 프로필·생리기록·일별기록·프로필 사진(base64)을 담은 JSON.
 */
object BackupCodec {

    private val MAGIC = byteArrayOf('O'.code.toByte(), 'L'.code.toByte(), 'O'.code.toByte(), 'B'.code.toByte())
    private const val VERSION = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val PBKDF2_ITERS = 210_000
    private const val KEY_BITS = 256

    /** 잘못된 백업 파일(형식 불일치)일 때. 암호 오류는 [javax.crypto.AEADBadTagException]로 구분된다. */
    class BadBackupException(message: String) : Exception(message)

    // ---- 직렬화 --------------------------------------------------------------

    class Bundle(
        val profiles: List<Profile>,
        val periodStarts: List<PeriodStart>,
        val dayRecords: List<DayRecord>,
        /** 사진 파일 basename → 원본 바이트. 복원 시 내부 저장소에 다시 쓴다. */
        val photos: Map<String, ByteArray>,
    )

    /** 도메인 데이터를 JSON 바이트로. 사진은 basename을 참조로 두고 바이트는 photos 맵에 base64로 싣는다. */
    fun encodeJson(bundle: Bundle): ByteArray {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val profiles = JSONArray()
        bundle.profiles.forEach { p ->
            profiles.put(
                JSONObject()
                    .put("id", p.id).put("name", p.name).put("color", p.color)
                    .put("defaultCycleLength", p.defaultCycleLength)
                    .put("defaultPeriodLength", p.defaultPeriodLength)
                    .put("onBirthControl", p.onBirthControl)
                    .put("locked", p.locked)
                    .put("photo", p.photoPath?.let { java.io.File(it).name } ?: JSONObject.NULL)
                    .put("sortOrder", p.sortOrder),
            )
        }
        root.put("profiles", profiles)

        val starts = JSONArray()
        bundle.periodStarts.forEach { s ->
            starts.put(
                JSONObject().put("id", s.id).put("profileId", s.profileId)
                    .put("startDate", s.startDate.toEpochDay())
                    .put("endDate", s.endDate?.toEpochDay() ?: JSONObject.NULL),
            )
        }
        root.put("periodStarts", starts)

        val records = JSONArray()
        bundle.dayRecords.forEach { r ->
            records.put(
                JSONObject().put("id", r.id).put("profileId", r.profileId)
                    .put("date", r.date.toEpochDay())
                    .put("flow", r.flow ?: JSONObject.NULL)
                    .put("symptoms", r.symptoms ?: JSONObject.NULL)
                    .put("mood", r.mood ?: JSONObject.NULL)
                    .put("temperature", r.temperature ?: JSONObject.NULL)
                    .put("weight", r.weight ?: JSONObject.NULL)
                    .put("medication", r.medication ?: JSONObject.NULL)
                    .put("memo", r.memo ?: JSONObject.NULL),
            )
        }
        root.put("dayRecords", records)

        val photos = JSONObject()
        bundle.photos.forEach { (name, bytes) -> photos.put(name, Base64.encodeToString(bytes, Base64.NO_WRAP)) }
        root.put("photos", photos)

        return root.toString().toByteArray(Charsets.UTF_8)
    }

    /** JSON 바이트를 도메인 데이터로. photoPath는 basename만 담아 두고, 실제 경로는 복원 단계에서 채운다. */
    fun decodeJson(json: ByteArray): Bundle {
        val root = try { JSONObject(String(json, Charsets.UTF_8)) } catch (e: Exception) { throw BadBackupException("JSON 형식 오류") }

        val profiles = root.getJSONArray("profiles").mapObjects { o ->
            Profile(
                id = o.getLong("id"), name = o.getString("name"), color = o.getInt("color"),
                defaultCycleLength = o.getInt("defaultCycleLength"),
                defaultPeriodLength = o.getInt("defaultPeriodLength"),
                onBirthControl = o.getBoolean("onBirthControl"),
                locked = o.optBoolean("locked", false),
                photoPath = o.optStringOrNull("photo"), // 여기선 basename, 복원 시 절대경로로 치환
                sortOrder = o.optInt("sortOrder", 0),
            )
        }
        val starts = root.getJSONArray("periodStarts").mapObjects { o ->
            PeriodStart(
                id = o.getLong("id"), profileId = o.getLong("profileId"),
                startDate = java.time.LocalDate.ofEpochDay(o.getLong("startDate")),
                endDate = if (o.isNull("endDate")) null else java.time.LocalDate.ofEpochDay(o.getLong("endDate")),
            )
        }
        val records = root.getJSONArray("dayRecords").mapObjects { o ->
            DayRecord(
                id = o.getLong("id"), profileId = o.getLong("profileId"),
                date = java.time.LocalDate.ofEpochDay(o.getLong("date")),
                flow = if (o.isNull("flow")) null else o.getInt("flow"),
                symptoms = o.optStringOrNull("symptoms"),
                mood = o.optStringOrNull("mood"),
                temperature = if (o.isNull("temperature")) null else o.getDouble("temperature"),
                weight = if (o.isNull("weight")) null else o.getDouble("weight"),
                medication = o.optStringOrNull("medication"),
                memo = o.optStringOrNull("memo"),
            )
        }
        val photos = mutableMapOf<String, ByteArray>()
        root.optJSONObject("photos")?.let { po ->
            po.keys().forEach { k -> photos[k] = Base64.decode(po.getString(k), Base64.NO_WRAP) }
        }
        return Bundle(profiles, starts, records, photos)
    }

    // ---- 암호화 --------------------------------------------------------------

    /** 평문 바이트를 암호로 봉인. 매번 새 salt·IV. */
    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)
        return MAGIC + byteArrayOf(VERSION.toByte()) + salt + iv + ct
    }

    /**
     * 봉인 해제. 암호가 틀리거나 파일이 변조되면 [javax.crypto.AEADBadTagException]가 난다(무결성 검증).
     * 형식 자체가 백업 파일이 아니면 [BadBackupException].
     */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        val header = MAGIC.size + 1 + SALT_LEN + IV_LEN
        if (blob.size < header) throw BadBackupException("파일이 손상되었거나 백업 형식이 아닙니다.")
        if (!(blob[0] == MAGIC[0] && blob[1] == MAGIC[1] && blob[2] == MAGIC[2] && blob[3] == MAGIC[3])) {
            throw BadBackupException("OLO Cycle 백업 파일이 아닙니다.")
        }
        var p = MAGIC.size + 1 // 버전 바이트는 현재 1만 존재
        val salt = blob.copyOfRange(p, p + SALT_LEN); p += SALT_LEN
        val iv = blob.copyOfRange(p, p + IV_LEN); p += IV_LEN
        val ct = blob.copyOfRange(p, blob.size)
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(passphrase, salt, PBKDF2_ITERS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    // ---- 작은 도우미 ---------------------------------------------------------

    private inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
        (0 until length()).map { f(getJSONObject(it)) }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").ifEmpty { null }
}
