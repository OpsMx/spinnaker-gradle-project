/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gradle.ospackage

import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayPlugin
import com.jfrog.bintray.gradle.BintrayUploadTask
import com.netflix.gradle.plugins.deb.Deb
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.tasks.Upload

/**
 * This is a workaround for:
 * https://github.com/bintray/gradle-bintray-plugin/issues/84
 *
 * and as such should die-in-a-fire if that issue gets resolved
 */
class OspackageBintrayPublishPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.apply(BintrayPlugin)

        def packageExtension = project.extensions.create('bintrayPackage', OspackageBintrayExtension)

        project.tasks.withType(Deb) { Deb deb ->
            def spinnakerDebians = project.configurations.maybeCreate('spinnakerDebians')
            project.artifacts.add('spinnakerDebians', deb)
            def extension = (BintrayExtension) project.extensions.getByName('bintray')
            def buildDebPublish = project.tasks.create("publish${deb.name}", BintrayUploadTask) { BintrayUploadTask task ->
                task.with {
                    apiUrl = extension.apiUrl
                    user = extension.user
                    apiKey = extension.key
                    configurations = ['spinnakerDebians']
                    publish = extension.publish
                    dryRun = extension.dryRun
                    userOrg = extension.pkg.userOrg ?: extension.user
                    repoName = packageExtension.packageRepo ?: extension.pkg.repo
                    packageName = extension.pkg.name
                    packageDesc = extension.pkg.desc
                    packageWebsiteUrl = extension.pkg.websiteUrl
                    packageIssueTrackerUrl = extension.pkg.issueTrackerUrl
                    packageVcsUrl = extension.pkg.vcsUrl
                    packageLicenses = extension.pkg.licenses
                    packageLabels = extension.pkg.labels
                    packageAttributes = extension.pkg.attributes
                    packagePublicDownloadNumbers = extension.pkg.publicDownloadNumbers
                    versionName = extension.pkg.version.name ?: project.version
                    versionDesc = extension.pkg.version.desc
                    versionReleased = extension.pkg.version.released
                    versionVcsTag = extension.pkg.version.vcsTag ?: project.version
                    versionAttributes = extension.pkg.version.attributes
                    signVersion = extension.pkg.version.gpg.sign
                    gpgPassphrase = extension.pkg.version.gpg.passphrase
                    syncToMavenCentral = extension.pkg.version.mavenCentralSync.sync == null ?
                            true : extension.pkg.version.mavenCentralSync.sync
                    ossUser = extension.pkg.version.mavenCentralSync.user
                    ossPassword = extension.pkg.version.mavenCentralSync.password
                    ossCloseRepo = extension.pkg.version.mavenCentralSync.close
                }
            }

            buildDebPublish.mustRunAfter('build')
            buildDebPublish.dependsOn(deb)
            buildDebPublish.dependsOn(spinnakerDebians.allArtifacts)
            Upload installTask = project.tasks.withType(Upload)?.findByName('install')
            if (installTask) {
                buildDebPublish.dependsOn(installTask)
            }
            buildDebPublish.group = BintrayUploadTask.GROUP
            project.rootProject.tasks.release.dependsOn(buildDebPublish)
            project.gradle.taskGraph.whenReady { TaskExecutionGraph graph ->
                buildDebPublish.onlyIf {
                    graph.hasTask(':final') || graph.hasTask(':candidate')
                }
            }
        }
    }
}