package dev.assay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoTargetTest {
    @Test
    fun `branch names follow git's ref rules`() {
        listOf("assay/output", "assay/sample-output", "release-1.2", "a/b/c").forEach {
            assertTrue(it, RepoTarget.isValidBranch(it))
        }
        listOf("a//b", "x.lock", "a/.b", "a/b.lock/c", "ends.", "ends/", "-lead", "has space", "a..b", "").forEach {
            assertFalse(it, RepoTarget.isValidBranch(it))
        }
        assertThrows(IllegalArgumentException::class.java) { RepoTarget(RepoRef("o", "r"), "a//b") }
    }

    @Test
    fun `the same repo on different branches is a different target`() {
        val store = InMemoryRepoStore()
        store.add(RepoTarget(RepoRef("o", "r"), "assay/output"))
        store.add(RepoTarget(RepoRef("o", "r"), "assay/other"))
        store.add(RepoTarget(RepoRef("o", "r"), "assay/output"))
        assertEquals(2, store.list().size)
    }
}
