package com.github.helpermethod

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.revwalk.RevCommit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.function.Consumer

class NativeGitProviderTest {

    @TempDir
    lateinit var gitDirectory : Path;
    lateinit var gitRepo: Git

    lateinit var providerUnderTest: NativeGitProvider

    @BeforeEach
    internal fun setUp() {
        gitRepo = Git.init()
                .setDirectory(gitDirectory.toFile())
                .call()

        providerUnderTest = NativeGitProvider(gitDirectory)
    }

    @Test
    @DisplayName("can add files")
    internal fun canAddFiles() {
        val newFile = File(gitDirectory.toFile(), "newFile")
        newFile.createNewFile()

        providerUnderTest.add(newFile.name)

        assertThat(gitRepo.status().call().added)
                .containsExactly(newFile.name)
    }

    @Test
    @DisplayName("can commit")
    internal fun canCommit() {
        val fileToCommit = File(gitDirectory.toFile(), "fileToCommit")
        fileToCommit.createNewFile()
        gitRepo.add().addFilepattern(fileToCommit.name).call()

        providerUnderTest.commit("Sandra Parsick <sandra@email.com>", "Hello George")

        assertThat(gitRepo.status().call().isClean)
                .describedAs("repository status should be clean")
                .isTrue()

        assertThat(gitRepo.log().setMaxCount(1).call().first())
                .describedAs("commit author and message should match")
                .extracting("authorIdent.name", "authorIdent.emailAddress", "shortMessage")
                .isEqualTo(listOf("Sandra Parsick", "sandra@email.com", "Hello George"))
    }

    @Test
    @DisplayName("can checkout")
    internal fun canCheckoutNewBranch() {
        providerUnderTest.checkoutNewBranch("newBranch")

        assertThat(gitRepo.repository.branch).isEqualTo("newBranch")

    }
}