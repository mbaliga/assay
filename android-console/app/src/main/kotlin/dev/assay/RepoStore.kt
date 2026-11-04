package dev.assay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A repository the user chose to audit, and the branch that receives its `.assay/` output. */
data class RepoTarget(val repo: RepoRef, val outputBranch: String) {
    init {
        require(isValidBranch(outputBranch)) { "Output branch is not a valid branch name" }
    }

    val key: String get() = "${repo.slug}@$outputBranch"

    companion object {
        const val DEFAULT_BRANCH = "assay/output"
        private val BRANCH = Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}")

        /** The git ref-name rules that matter here: no empty, dot-leading or `.lock` segments, no `..`, no trailing dot. */
        fun isValidBranch(name: String): Boolean =
            BRANCH.matches(name) && !name.contains("..") && !name.endsWith(".") && !name.endsWith("/") &&
                name.split('/').none { it.isEmpty() || it.startsWith(".") || it.endsWith(".lock") }
    }
}

interface RepoStore {
    fun list(): List<RepoTarget>
    fun add(target: RepoTarget)
    fun remove(target: RepoTarget)
}

class InMemoryRepoStore(initial: List<RepoTarget> = emptyList()) : RepoStore {
    private val items = initial.toMutableList()
    override fun list(): List<RepoTarget> = items.toList()
    override fun add(target: RepoTarget) {
        if (items.none { it.key == target.key }) items += target
    }

    override fun remove(target: RepoTarget) {
        items.removeAll { it.key == target.key }
    }
}

class PreferencesRepoStore(context: Context) : RepoStore {
    private val prefs = context.applicationContext.getSharedPreferences("assay_repos", Context.MODE_PRIVATE)

    override fun list(): List<RepoTarget> {
        val text = prefs.getString("targets", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                RepoTarget(RepoRef.parse(item.getString("repo")), item.getString("branch"))
            }
        }.getOrDefault(emptyList())
    }

    override fun add(target: RepoTarget) = save(list().filterNot { it.key == target.key } + target)

    override fun remove(target: RepoTarget) = save(list().filterNot { it.key == target.key })

    private fun save(targets: List<RepoTarget>) {
        val array = JSONArray()
        targets.forEach { array.put(JSONObject().put("repo", it.repo.slug).put("branch", it.outputBranch)) }
        prefs.edit().putString("targets", array.toString()).apply()
    }
}
