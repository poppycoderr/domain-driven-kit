#!/usr/bin/env python3
"""Checks that the BOM (ddk-dependencies) agrees with the parent pom.

The BOM does not inherit the parent pom, so its versions are written twice. This fails when
- a version property of the BOM differs from the parent's property of the same name,
- the BOM's own version or ddk.version differs from the project version,
- a plugin version in the BOM differs from the parent's,
- a published DDK module is missing from the BOM, or the BOM lists a module that does not exist.

Usage: scripts/check-bom.py [repository root]
"""

import re
import sys
from pathlib import Path
from xml.etree import ElementTree

NS = {"m": "http://maven.apache.org/POM/4.0.0"}
# Modules that are not libraries: archetypes are used through archetype:generate, examples are not published
UNPUBLISHED = ("ddk-archetypes", "ddk-examples", "ddk-dependencies")


def load(path):
    return ElementTree.parse(path).getroot()


def text(element, path):
    found = element.find(path, NS)
    return found.text.strip() if found is not None and found.text else None


def properties(pom):
    props = pom.find("m:properties", NS)
    return {} if props is None else {child.tag.split("}")[1]: (child.text or "").strip() for child in props}


def resolve(value, props):
    return re.sub(r"\$\{([^}]+)}", lambda m: props.get(m.group(1), m.group(0)), value or "")


def plugin_versions(pom, props):
    versions = {}
    for plugin in pom.iter("{%s}plugin" % NS["m"]):
        artifact, version = text(plugin, "m:artifactId"), text(plugin, "m:version")
        if artifact and version:
            versions[artifact] = resolve(version, props)
    return versions


def published_modules(root, directory):
    pom = load(directory / "pom.xml")
    artifact = text(pom, "m:artifactId")
    packaging = text(pom, "m:packaging") or "jar"
    if directory != root and directory.relative_to(root).parts[0] in UNPUBLISHED:
        return []
    found = [artifact] if packaging == "jar" else []
    for module in pom.findall("m:modules/m:module", NS):
        found += published_modules(root, directory / module.text.strip())
    return found


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    parent = load(root / "pom.xml")
    bom = load(root / "ddk-dependencies" / "pom.xml")
    parent_props, bom_props = properties(parent), properties(bom)
    project_version = text(parent, "m:version")
    group = text(parent, "m:groupId")
    errors = []

    if text(bom, "m:version") != project_version:
        errors.append(f"BOM version {text(bom, 'm:version')} != project version {project_version}")
    if bom_props.get("ddk.version") != project_version:
        errors.append(f"BOM ddk.version {bom_props.get('ddk.version')} != project version {project_version}")
    if text(parent, "m:parent/m:version") != parent_props.get("spring-boot.version"):
        errors.append("spring-boot-starter-parent version != spring-boot.version")

    for name, value in sorted(bom_props.items()):
        if name == "ddk.version" or not name.endswith(".version"):
            continue
        if name not in parent_props:
            errors.append(f"BOM property {name} has no counterpart in the parent pom")
        elif parent_props[name] != value:
            errors.append(f"{name}: BOM {value} != parent {parent_props[name]}")

    parent_plugins = plugin_versions(parent, parent_props)
    for artifact, version in sorted(plugin_versions(bom, bom_props).items()):
        if artifact in parent_plugins and parent_plugins[artifact] != version:
            errors.append(f"plugin {artifact}: BOM {version} != parent {parent_plugins[artifact]}")

    managed = {}
    for dependency in bom.findall("m:dependencyManagement/m:dependencies/m:dependency", NS):
        if text(dependency, "m:groupId") == group:
            managed[text(dependency, "m:artifactId")] = text(dependency, "m:version")
    modules = published_modules(root, root)
    for artifact in modules:
        if artifact not in managed:
            errors.append(f"module {artifact} is missing from the BOM")
        elif managed[artifact] != "${ddk.version}":
            errors.append(f"module {artifact} has version {managed[artifact]} in the BOM, expected ${{ddk.version}}")
    for artifact in sorted(set(managed) - set(modules)):
        errors.append(f"BOM lists {artifact}, which is not a published module")

    if errors:
        print("BOM and parent pom disagree:")
        for error in errors:
            print(f"  - {error}")
        return 1
    print(f"BOM agrees with the parent pom: {len(modules)} modules, version {project_version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
