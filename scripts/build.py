"""Build with the Android SDK directly; no machine-specific paths or Gradle cache."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
HASHES = {
    "api": "4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b",
    "provider": "b0f18cd9812464ec171c53cac93a819fe411718a3965c311f01eb4de265381b3",
    "shared": "4659642c9339be0a26e9c65bb8648f7ad6d8f4a465f557993ccbc78802381635",
    "aidl": "33fe7191cdd69fcb66d649264f3b0c47acb2f3d6343afc05b98dbbff6f221963",
}


def run(*args):
    subprocess.run([str(a) for a in args], check=True, cwd=ROOT)


def java_tool(name):
    suffix = ".exe" if os.name == "nt" else ""
    home = os.environ.get("JAVA_HOME")
    candidate = Path(home) / "bin" / (name + suffix) if home else None
    found = str(candidate) if candidate and candidate.is_file() else shutil.which(name)
    if not found:
        raise SystemExit(f"JDK tool {name} missing. Set JAVA_HOME to a JDK.")
    return found


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keystore", type=Path, help="Private release key, outside repository")
    parser.add_argument("--alias", default="androiddebugkey")
    parser.add_argument("--test-only", action="store_true")
    args = parser.parse_args()
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    sources = ROOT / "app/src/main/java"
    replay = build / "replay"
    replay.mkdir(exist_ok=True)
    run(java_tool("javac"), "-encoding", "UTF-8", "-d", replay,
        sources / "local/cheertok/probe/TapRouter.java", ROOT / "tests/TapRouterReplay.java")
    run(java_tool("java"), "-cp", replay, "local.cheertok.probe.TapRouterReplay")
    if args.test_only:
        return
    sdk_value = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not sdk_value:
        raise SystemExit("Set ANDROID_SDK_ROOT to an Android SDK installation.")
    sdk = Path(sdk_value)
    tools = sdk / "build-tools/31.0.0"
    android = sdk / "platforms/android-31/android.jar"
    if not android.is_file() or not (tools / "lib/d8.jar").is_file():
        raise SystemExit('Install SDK packages: "platforms;android-31" "build-tools;31.0.0"')
    libs = []
    for name, expected in HASHES.items():
        cache = ROOT / ".local/dependencies" / name
        cache.mkdir(parents=True, exist_ok=True)
        archive = cache / "library.aar"
        if not archive.exists():
            url = f"https://repo.maven.apache.org/maven2/dev/rikka/shizuku/{name}/13.1.5/{name}-13.1.5.aar"
            urllib.request.urlretrieve(url, archive)
        if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Checksum mismatch: {name}; remove cached archive and retry")
        with zipfile.ZipFile(archive) as zipped:
            (cache / "classes.jar").write_bytes(zipped.read("classes.jar"))
        libs.append(cache / "classes.jar")
    classes = build / "classes"
    dex = build / "dex"
    classes.mkdir(exist_ok=True)
    dex.mkdir(exist_ok=True)
    classpath = os.pathsep.join(str(p) for p in [android] + libs)
    run(java_tool("javac"), "-encoding", "UTF-8", "-source", "8", "-target", "8",
        "-classpath", classpath, "-d", classes, *sorted(sources.rglob("*.java")))
    jar = build / "application.jar"
    run(java_tool("jar"), "cf", jar, "-C", classes, ".")
    run(java_tool("java"), "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8",
        "--min-api", "26", "--lib", android, "--output", dex, jar, *libs)
    suffix = ".exe" if os.name == "nt" else ""
    unsigned = build / "unsigned.apk"
    run(tools / ("aapt" + suffix), "package", "-f", "-M",
        ROOT / "app/src/main/AndroidManifest.xml", "-I", android, "-F", unsigned)
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as zipped:
        zipped.write(dex / "classes.dex", "classes.dex")
    aligned = build / "aligned.apk"
    run(tools / ("zipalign" + suffix), "-f", "4", unsigned, aligned)
    keystore = args.keystore
    alias = args.alias
    if keystore:
        if not os.environ.get("CHEERTOK_STORE_PASSWORD") or not os.environ.get("CHEERTOK_KEY_PASSWORD"):
            raise SystemExit("Set CHEERTOK_STORE_PASSWORD and CHEERTOK_KEY_PASSWORD for release signing")
    else:
        keystore = ROOT / ".local/debug.keystore"
        os.environ["CHEERTOK_STORE_PASSWORD"] = "android"
        os.environ["CHEERTOK_KEY_PASSWORD"] = "android"
        if not keystore.exists():
            run(java_tool("keytool"), "-genkeypair", "-keystore", keystore,
                "-storepass", "android", "-keypass", "android", "-alias", alias,
                "-dname", "CN=Android Debug", "-keyalg", "RSA", "-validity", "10000")
    output = build / "CheerTok-Recovery-0.6.apk"
    signer = tools / "lib/apksigner.jar"
    run(java_tool("java"), "-jar", signer, "sign", "--ks", keystore,
        "--ks-key-alias", alias, "--ks-pass", "env:CHEERTOK_STORE_PASSWORD",
        "--key-pass", "env:CHEERTOK_KEY_PASSWORD", "--out", output, aligned)
    run(java_tool("java"), "-jar", signer, "verify", output)
    print(f"Built {output}")


if __name__ == "__main__":
    main()
