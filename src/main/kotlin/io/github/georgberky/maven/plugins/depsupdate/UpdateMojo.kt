package io.github.georgberky.maven.plugins.depsupdate

import org.apache.maven.artifact.factory.ArtifactFactory
import org.apache.maven.artifact.metadata.ArtifactMetadataSource
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.plugin.AbstractMojo
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.plugins.annotations.Component
import org.apache.maven.plugins.annotations.Mojo
import org.apache.maven.plugins.annotations.Parameter
import org.apache.maven.project.MavenProject
import org.apache.maven.settings.Settings

@Mojo(name = "update")
class UpdateMojo : AbstractMojo() {
    @Parameter(defaultValue = "\${project}", required = true)
    lateinit var mavenProject: MavenProject
    @Parameter(defaultValue = "\${localRepository}", required = true)
    lateinit var localRepository: ArtifactRepository
    @Parameter(defaultValue = "\${settings}", required = true)
    lateinit var settings: Settings
    @Parameter(property = "connectionUrl", defaultValue = "\${project.scm.connection}", required = false)
    var connectionUrl: String = ""
    @Parameter(property = "developerConnectionUrl", defaultValue = "\${project.scm.developerConnection}", required = false)
    var developerConnectionUrl: String = ""
    @Parameter(property = "connectionType", defaultValue = "connection", required = true)
    lateinit var connectionType: String
    @Parameter(property = "dependencyUpdate.git.provider", defaultValue="NATIVE", required = false)
    lateinit var gitProvider : GitProviderChoice

    @Component
    lateinit var artifactFactory: ArtifactFactory
    @Component
    lateinit var artifactMetadataSource: ArtifactMetadataSource

    private val connection: String
        get() = if (connectionType == "developerConnection") developerConnectionUrl else connectionUrl

    override fun execute() {
        withGit { git ->
            UpdateResolver(
                    mavenProject = mavenProject,
                    artifactMetadataSource = artifactMetadataSource,
                    localRepository = localRepository,
                    artifactFactory = artifactFactory
            )
            .updates
            .onEach { println("execute im mojo: latestVersion: '${it.latestVersion}' / version:'${it.version}'") }
            .filter(VersionUpdate::canSkip)
            .onEach { println("execute im mojo canSkip: ${it.latestVersion}") }
            .map { it to "dependency-update/${it.groupId}-${it.artifactId}-${it.latestVersion}" }
            .onEach { println("execute im mojo canSkip (nach map): ${it.second} ${it.first}") }
            .filter { (_, branchName) -> !git.hasRemoteBranch(branchName) }
            .onEach { println("execute im mojo nach filter branches: ${it.second} ${it.first}") }
            .forEach { (update, branchName) ->
                git.checkoutNewBranch(branchName)
                val pom = update.updatedPom()
                mavenProject.file.writeText(pom.html())
                git.add("pom.xml")
                git.commit(
                    "dependency-update-bot",
                    "Bump ${update.artifactId} from ${update.version} to ${update.latestVersion}"
                )
                git.push(branchName)
            }
        }
    }

    private fun withGit(f: (GitProvider) -> Unit) {
        configurationCheck()
        val git = gitProvider.createProvider(mavenProject.basedir.toPath(), settings, connection);
        git.use(f)
    }

    private fun configurationCheck() {
        if (gitProvider == GitProviderChoice.JGIT && connection.isEmpty()) {
            throw MojoExecutionException("You chose JGIT as GitProvider. Therefore, you have to set properties " +
                    "'developerConnectionUrl' or 'connectionUrl' dependent on the 'connectionType'")
        }
    }
}