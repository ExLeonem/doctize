package dev.doctize;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConventionsTest {

    @TempDir
    Path dir;

    private void write(String path, String content) throws IOException {
        Path file = dir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void remoteAddressesBecomeWebAddresses() {
        assertThat(Conventions.webAddress("git@github.com:Acme/cache.git")).hasValue("https://github.com/Acme/cache");
        assertThat(Conventions.webAddress("https://github.com/acme/cache.git")).hasValue("https://github.com/acme/cache");
        assertThat(Conventions.webAddress("ssh://git@gitlab.com:2222/acme/group/cache.git")).hasValue("https://gitlab.com/acme/group/cache");
        assertThat(Conventions.webAddress("https://codeberg.org/acme/cache/")).hasValue("https://codeberg.org/acme/cache");
    }

    @Test
    void credentialsInTheRemoteAreNeverPublished() {
        assertThat(Conventions.webAddress("https://bot:s3cret@github.com/acme/cache.git")).hasValue("https://github.com/acme/cache");
    }

    @Test
    void remotesOnPrivateHostsOrWithoutARepositoryAreIgnored() {
        assertThat(Conventions.webAddress("git@git.acme.internal:team/cache.git")).isEmpty();
        assertThat(Conventions.webAddress("/srv/git/cache.git")).isEmpty();
        assertThat(Conventions.webAddress("https://github.com/acme")).isEmpty();
    }

    @Test
    void theRepositoryIsTheOriginRemoteOfTheEnclosingGitRepository() throws IOException {
        write(".git/config", """
                [core]
                \tbare = false
                [remote "upstream"]
                \turl = https://github.com/upstream/cache.git
                [remote "origin"]
                \turl = git@github.com:acme/cache.git
                \tfetch = +refs/heads/*:refs/remotes/origin/*
                [branch "main"]
                \tremote = origin
                """);
        Files.createDirectories(dir.resolve("modules/core"));

        assertThat(Conventions.repository(dir.resolve("modules/core"))).hasValue("https://github.com/acme/cache");
    }

    @Test
    void withoutAnOriginTheFirstRemoteIsUsed() throws IOException {
        write(".git/config", "[remote \"fork\"]\n\turl = https://github.com/me/cache\n[remote \"other\"]\n\turl = https://github.com/you/cache\n");

        assertThat(Conventions.repository(dir)).hasValue("https://github.com/me/cache");
    }

    @Test
    void aWorktreeUsesTheConfigurationOfItsMainRepository() throws IOException {
        write("main/.git/config", "[remote \"origin\"]\n\turl = https://github.com/acme/cache.git\n");
        write("main/.git/worktrees/feature/commondir", "../..\n");
        write("feature/.git", "gitdir: ../main/.git/worktrees/feature\n");

        assertThat(Conventions.repository(dir.resolve("feature"))).hasValue("https://github.com/acme/cache");
    }

    @Test
    void aRepositoryWithoutRemotesHasNoAddress() throws IOException {
        write(".git/config", "[core]\n\tbare = false\n");

        assertThat(Conventions.repository(dir)).isEmpty();
    }
}
