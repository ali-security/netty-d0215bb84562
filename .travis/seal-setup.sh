# Sourced from .travis.yml before_install.
# Travis no longer provisions oraclejdk7/openjdk7, so install Zulu 7 (the
# public jar was built with JDK 1.7.0_25) on top of the openjdk8 image, reuse
# the image's up-to-date cacerts (Zulu 7's are too old for Maven Central), and
# install a Maven that still runs on Java 7 and defaults to HTTPS Central.
SEAL_JDK_DIR=/tmp/seal-jdk
BASE_CACERTS=$(find -L "$JAVA_HOME" -name cacerts -type f | head -1)
mkdir -p "$SEAL_JDK_DIR"
curl -fsSL "https://cdn.azul.com/zulu/bin/${SEAL_JDK}.tar.gz" | tar xz --strip-components=1 -C "$SEAL_JDK_DIR"
cp "$BASE_CACERTS" "$SEAL_JDK_DIR/jre/lib/security/cacerts"
export JAVA_HOME="$SEAL_JDK_DIR"
curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${SEAL_MAVEN}/binaries/apache-maven-${SEAL_MAVEN}-bin.tar.gz" | tar xz -C /tmp
export M2_HOME="/tmp/apache-maven-${SEAL_MAVEN}"
export PATH="$JAVA_HOME/bin:$M2_HOME/bin:$PATH"
# Surefire runs with forkMode=never, so the tests share the Maven JVM and its
# MAVEN_OPTS; seal-java.security re-allows the release-era key sizes the SSL
# echo tests' 512-bit RSA test certificate needs.
export MAVEN_OPTS="-Dhttps.protocols=TLSv1.2 -Djava.security.properties=$TRAVIS_BUILD_DIR/.travis/seal-java.security"
java -version
mvn -version
