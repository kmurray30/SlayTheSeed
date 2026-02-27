# Third-Party Library Versions (from desktop-1.0.jar)

Extracted from `desktop-1.0/META-INF/maven/*/*/pom.properties`.

Used to ensure exact version match when replacing decompiled spire_src with Maven dependencies.

## From JAR metadata

| GroupId | ArtifactId | Version |
|---------|------------|---------|
| com.badlogicgames.gdx | gdx | 1.9.5 |
| com.badlogicgames.gdx | gdx-backend-headless | 1.9.5 |
| com.badlogicgames.gdx | gdx-backend-lwjgl | 1.9.5 |
| com.badlogicgames.gdx | gdx-controllers | 1.9.5 |
| com.badlogicgames.gdx | gdx-controllers-desktop | 1.9.5 |
| com.badlogicgames.gdx | gdx-freetype | 1.9.5 |
| com.badlogicgames.gdx | gdx-tools | 1.9.5 |
| com.badlogicgames.jlayer | jlayer | 1.0.1-gdx |
| com.code-disaster.steamworks4j | steamworks4j | 1.9.0 |
| com.gikk | twirc | 0.5.0 |
| commons-codec | commons-codec | 1.10 |
| commons-net | commons-net | 3.5 |
| net.arikia.dev | discord-rpc | 1.5.1 |
| net.arikia.dev | drpc-releases | 0.1 |
| net.java.jutils | jutils | 1.0.0 |
| org.apache.logging.log4j | log4j-api | 2.17.0 |
| org.apache.logging.log4j | log4j-core | 2.17.0 |
| org.jcraft | jorbis | 0.0.17 |
| org.slf4j | slf4j-api | 1.7.25 |
| org.slf4j | slf4j-simple | 1.7.25 |

## Inferred (transitive / from libGDX parent)

| GroupId | ArtifactId | Version | Source |
|---------|------------|---------|--------|
| com.badlogicgames.gdx | gdx-jnigen | 1.9.5 | gdx pom (same as gdx) |
| org.lwjgl.lwjgl | lwjgl | 2.9.2 | libGDX 1.9.5 parent pom |
| org.lwjgl.lwjgl | lwjgl_util | 2.9.2 | libGDX 1.9.5 parent pom |
| com.google.code.gson | gson | 2.8.x | Game uses; 2.8.9 typical for era |
| net.java.jinput | jinput | 2.0.9 | JInput; gdx-controllers transitive |

## Package mapping (spire_src → Maven)

Excluded from spire_src compilation, provided by Maven:

- `com.badlogic` → libGDX (gdx, gdx-backend-headless, etc.)
- `com.codedisaster` → steamworks4j
- `com.esotericsoftware` → spine (gdx-freetype / spine runtime)
- `com.google` → gson
- `com.jcraft` → jorbis (+ jogg)
- `com.sun` → jna (JNA)
- `de.robojumper` → ststwitch (Twitch; may need stub)
- `javazoom` → jlayer
- `net.arikia` → discord-rpc
- `net.java` → jinput, jutils
- `org.apache` → log4j, commons-codec, commons-net
- `org.lwjgl` → lwjgl
- `org.slf4j` → slf4j

Only `com.megacrit` (game code) is compiled from spire_src.

**Kept decompiled** (not on Maven Central): `com.gikk` (twirc), `net.arikia` (discord-rpc).

**Additional Maven deps** (for clean build): spine-libgdx 3.8.55.1, gdx-backend-lwjgl 1.9.5, JNA 4.5.2 (for discord-rpc).

**Kept from spire_src** (custom/integration code): `de.robojumper` (ststwitch).
