#!/bin/zsh
# Flow: publishes only the module Flow patches, compose ui-skiko (its root metadata, desktop JVM and macosArm64), to
# mavenLocal as <version>; every other module and target stays the 1.13.0-beta02 release. FlowGraphics takes it with
# -Pflow.compose.local=true -Pflow.compose.localVersion=<version> (build-settings' flow.compose).
# A cold build is ~5 min (configuration and the ui-skiko dependency closure), an incremental one seconds.
# usage: flow-publish-local.sh [version]   (default 1.13.0-beta02-flow.1-local; keep the <release>-flow.<n>-local form)
version=${1:-1.13.0-beta02-flow.1-local}
# JDK 21 (the fork's toolchain) and an Android SDK (the build configures Android even when it builds no Android target).
export JAVA_HOME=${FLOW_CMP_JDK:-$(/usr/libexec/java_home -v 21)}
export ANDROIDX_JDK21=$JAVA_HOME
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}
cd ${0:A:h} || exit 1
./gradlew --console=plain \
  :compose:ui:ui-skiko:publishKotlinMultiplatformPublicationToMavenLocal \
  :compose:ui:ui-skiko:publishDesktopPublicationToMavenLocal \
  :compose:ui:ui-skiko:publishMacosArm64PublicationToMavenLocal \
  -Pjetbrains.publication.version.COMPOSE=$version
