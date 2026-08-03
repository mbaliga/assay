package dev.assay

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipFile

enum class LocalSeverity { CRITICAL, HIGH, MEDIUM, LOW, INFO }

data class LocalApkFinding(
    val ruleId: String,
    val severity: LocalSeverity,
    val title: String,
    val message: String,
    val evidence: String,
)

data class LocalApkReport(
    val displayName: String,
    val appLabel: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val apkBytes: Long,
    val sha256: String,
    val signerCount: Int,
    val requestedPermissions: List<String>,
    val exportedComponentCount: Int,
    val nativeLibraryCount: Int,
    val generatedAt: String,
    val findings: List<LocalApkFinding>,
)

object LocalApkAuditor {
    private const val MAX_APK_BYTES = 1024L * 1024L * 1024L

    private val broadPermissions = setOf(
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.WRITE_SETTINGS",
        "android.permission.PACKAGE_USAGE_STATS",
    )

    fun audit(context: Context, uri: Uri): LocalApkReport {
        val temp = File.createTempFile("assay-apk-", ".apk", context.cacheDir)
        try {
            val copy = copyBounded(context, uri, temp)
            val packageInfo = readPackageInfo(context.packageManager, temp.absolutePath)
                ?: error(context.getString(R.string.local_apk_parse_failed))
            val applicationInfo = packageInfo.applicationInfo
                ?: error(context.getString(R.string.local_apk_parse_failed))
            applicationInfo.sourceDir = temp.absolutePath
            applicationInfo.publicSourceDir = temp.absolutePath

            val requestedPermissions = packageInfo.requestedPermissions.orEmpty().sorted()
            val dangerousPermissions = requestedPermissions.filter { permission ->
                isDangerousPermission(context.packageManager, permission)
            }
            val broad = requestedPermissions.filter { it in broadPermissions }
            val exported = exportedWithoutPermission(packageInfo)
            val nativeLibraries = countNativeLibraries(temp)
            val signerCount = signerCount(packageInfo)
            val findings = buildList {
                if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-DEBUGGABLE",
                            severity = LocalSeverity.HIGH,
                            title = context.getString(R.string.local_finding_debuggable_title),
                            message = context.getString(R.string.local_finding_debuggable_body),
                            evidence = "ApplicationInfo.FLAG_DEBUGGABLE",
                        ),
                    )
                }
                if (applicationInfo.flags and ApplicationInfo.FLAG_TEST_ONLY != 0) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-TEST-ONLY",
                            severity = LocalSeverity.HIGH,
                            title = context.getString(R.string.local_finding_test_only_title),
                            message = context.getString(R.string.local_finding_test_only_body),
                            evidence = "ApplicationInfo.FLAG_TEST_ONLY",
                        ),
                    )
                }
                if (applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-BACKUP",
                            severity = LocalSeverity.MEDIUM,
                            title = context.getString(R.string.local_finding_backup_title),
                            message = context.getString(R.string.local_finding_backup_body),
                            evidence = "ApplicationInfo.FLAG_ALLOW_BACKUP",
                        ),
                    )
                }
                if (applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-CLEARTEXT",
                            severity = LocalSeverity.MEDIUM,
                            title = context.getString(R.string.local_finding_cleartext_title),
                            message = context.getString(R.string.local_finding_cleartext_body),
                            evidence = "ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC",
                        ),
                    )
                }
                if (exported.isNotEmpty()) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-EXPORTED-UNGUARDED",
                            severity = LocalSeverity.MEDIUM,
                            title = context.getString(R.string.local_finding_exported_title),
                            message = context.getString(R.string.local_finding_exported_body, exported.size),
                            evidence = exported.take(12).joinToString("\n"),
                        ),
                    )
                }
                if (broad.isNotEmpty()) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-BROAD-PERMISSIONS",
                            severity = LocalSeverity.HIGH,
                            title = context.getString(R.string.local_finding_broad_permissions_title),
                            message = context.getString(R.string.local_finding_broad_permissions_body, broad.size),
                            evidence = broad.joinToString("\n"),
                        ),
                    )
                }
                if (dangerousPermissions.isNotEmpty()) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-DANGEROUS-PERMISSIONS",
                            severity = LocalSeverity.LOW,
                            title = context.getString(R.string.local_finding_dangerous_permissions_title),
                            message = context.getString(R.string.local_finding_dangerous_permissions_body, dangerousPermissions.size),
                            evidence = dangerousPermissions.take(20).joinToString("\n"),
                        ),
                    )
                }
                if (signerCount == 0) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-NO-SIGNER",
                            severity = LocalSeverity.HIGH,
                            title = context.getString(R.string.local_finding_unsigned_title),
                            message = context.getString(R.string.local_finding_unsigned_body),
                            evidence = context.getString(R.string.local_no_signing_certificates),
                        ),
                    )
                }
                if (nativeLibraries > 0) {
                    add(
                        LocalApkFinding(
                            ruleId = "LOCAL-APK-NATIVE-CODE",
                            severity = LocalSeverity.INFO,
                            title = context.getString(R.string.local_finding_native_title),
                            message = context.getString(R.string.local_finding_native_body, nativeLibraries),
                            evidence = context.getString(R.string.local_native_library_count, nativeLibraries),
                        ),
                    )
                }
            }.sortedWith(compareBy<LocalApkFinding> { severityRank(it.severity) }.thenBy { it.ruleId })

            return LocalApkReport(
                displayName = displayName(context, uri),
                appLabel = runCatching { applicationInfo.loadLabel(context.packageManager).toString() }
                    .getOrDefault(packageInfo.packageName),
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName ?: context.getString(R.string.not_available),
                versionCode = packageVersionCode(packageInfo),
                minSdk = applicationInfo.minSdkVersion,
                targetSdk = applicationInfo.targetSdkVersion,
                apkBytes = copy.bytes,
                sha256 = copy.sha256,
                signerCount = signerCount,
                requestedPermissions = requestedPermissions,
                exportedComponentCount = exported.size,
                nativeLibraryCount = nativeLibraries,
                generatedAt = Instant.now().toString(),
                findings = findings,
            )
        } finally {
            temp.delete()
        }
    }

    private data class CopyResult(val bytes: Long, val sha256: String)

    private fun copyBounded(context: Context, uri: Uri, destination: File): CopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: error(context.getString(R.string.local_apk_open_failed))
        input.use { source ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_APK_BYTES) { context.getString(R.string.local_apk_too_large) }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        }
        require(total > 0) { context.getString(R.string.local_apk_empty) }
        return CopyResult(total, digest.digest().joinToString("") { byte -> "%02x".format(byte) })
    }

    @Suppress("DEPRECATION")
    private fun readPackageInfo(packageManager: PackageManager, path: String): PackageInfo? {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.GET_SIGNATURES or
            PackageManager.GET_SIGNING_CERTIFICATES
        return if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            packageManager.getPackageArchiveInfo(path, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun signerCount(packageInfo: PackageInfo): Int = if (Build.VERSION.SDK_INT >= 28) {
        packageInfo.signingInfo?.apkContentsSigners?.size ?: 0
    } else {
        packageInfo.signatures?.size ?: 0
    }

    @Suppress("DEPRECATION")
    private fun packageVersionCode(packageInfo: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) {
        packageInfo.longVersionCode
    } else {
        packageInfo.versionCode.toLong()
    }

    @Suppress("DEPRECATION")
    private fun isDangerousPermission(packageManager: PackageManager, permission: String): Boolean = runCatching {
        val info = packageManager.getPermissionInfo(permission, 0)
        info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS
    }.getOrDefault(false)

    private fun exportedWithoutPermission(packageInfo: PackageInfo): List<String> = buildList {
        packageInfo.activities.orEmpty().filter { it.exported && it.permission.isNullOrBlank() }.forEach {
            add("activity:${it.name}")
        }
        packageInfo.services.orEmpty().filter { it.exported && it.permission.isNullOrBlank() }.forEach {
            add("service:${it.name}")
        }
        packageInfo.receivers.orEmpty().filter { it.exported && it.permission.isNullOrBlank() }.forEach {
            add("receiver:${it.name}")
        }
        packageInfo.providers.orEmpty().filter {
            it.exported && it.readPermission.isNullOrBlank() && it.writePermission.isNullOrBlank()
        }.forEach {
            add("provider:${it.name}")
        }
    }.sorted()

    private fun countNativeLibraries(apk: File): Int = ZipFile(apk).use { zip ->
        zip.entries().asSequence().count { entry ->
            !entry.isDirectory && entry.name.startsWith("lib/") && entry.name.endsWith(".so")
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromProvider = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        return fromProvider?.takeIf { it.isNotBlank() } ?: context.getString(R.string.local_selected_apk)
    }

    private fun severityRank(severity: LocalSeverity): Int = when (severity) {
        LocalSeverity.CRITICAL -> 0
        LocalSeverity.HIGH -> 1
        LocalSeverity.MEDIUM -> 2
        LocalSeverity.LOW -> 3
        LocalSeverity.INFO -> 4
    }
}
