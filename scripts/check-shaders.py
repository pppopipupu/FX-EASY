"""Compile the mod's shader pairs through a real GL driver, the way the game does.

A shader that does not compile is worse than a visual bug: `ShaderManager` precompiles every registered
pipeline while the resource packs load and abandons the whole reload when one of them fails, so the client
then dies on its first GUI draw with an invalid program, far from the line that broke it. Only a real
context says anything about it, so this script inlines `#moj_import` the way `ShaderManager` does (a version
directive inside an imported file is commented out, the one of the shader being compiled is the only one
that may stand first) and compiles and links each vertex and fragment pair together.

Usage: python scripts/check-shaders.py [name ...]        needs `python -m pip install moderngl`
"""
import fnmatch
import pathlib
import re
import sys
import zipfile

try:
    import moderngl
except ImportError:
    sys.exit("moderngl is missing: python -m pip install moderngl")

ROOT = pathlib.Path(__file__).resolve().parents[1]
MOD_SHADERS = ROOT / "src/main/resources/assets/fxeasy/shaders"
CLIENT_JARS = pathlib.Path.home() / ".gradle/caches/neoformruntime/artifacts"
INCLUDE_ASSET = "assets/{}/shaders/include/{}"
IMPORT = re.compile(r"^\s*#moj_import\s*<([a-z0-9_.-]+):([^>]+)>\s*$")
VERSION = re.compile(r"^\s*#version\s+(\d+)")


def client_jar():
    jars = sorted(CLIENT_JARS.glob("minecraft_*_client.jar"))
    if not jars:
        sys.exit(f"no client jar under {CLIENT_JARS}, run a client task first")
    return jars[-1]


def read_include(namespace, path, jar):
    if namespace == "minecraft":
        with zipfile.ZipFile(jar) as archive:
            return archive.read(INCLUDE_ASSET.format(namespace, path)).decode("utf-8")
    file = MOD_SHADERS / "include" / path
    if not file.is_file():
        raise FileNotFoundError(f"no include {namespace}:{path}")
    return file.read_text(encoding="utf-8")


def inline(text, jar, imported=False):
    out = []
    version_seen = False
    for line in text.splitlines():
        match = IMPORT.match(line)
        if match:
            out.extend(inline(read_include(match.group(1), match.group(2), jar), jar, True).splitlines())
        elif imported and not version_seen and VERSION.match(line):
            version_seen = True
            out.append(VERSION.sub(lambda m: f"/*#version {m.group(1)}*/", line))
        else:
            out.append(line)
    return "\n".join(out)


def main():
    jar = client_jar()
    core = MOD_SHADERS / "core"
    names = sorted({p.stem for p in core.glob("*.vsh")} & {p.stem for p in core.glob("*.fsh")})
    if len(sys.argv) > 1:
        names = [n for n in names if any(fnmatch.fnmatch(n, p) for p in sys.argv[1:])]
    context = moderngl.create_standalone_context()
    print(f"{context.info['GL_VERSION']} | {context.info['GL_RENDERER']}")
    failed = 0
    for name in names:
        vertex = inline((core / f"{name}.vsh").read_text(encoding="utf-8"), jar)
        fragment = inline((core / f"{name}.fsh").read_text(encoding="utf-8"), jar)
        try:
            context.program(vertex_shader=vertex, fragment_shader=fragment)
            print(f"OK    {name}")
        except Exception as error:
            failed += 1
            print(f"FAIL  {name}\n{error}")
    print(f"{len(names)} pairs, {failed} failed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
