#!/usr/bin/env python3
"""Hardened trusted Phase 10.9 Guardian v12 entrypoint.

The implementation present before this bootstrap is preserved byte-for-byte in
phase109_guardian_v12_base.py. This entrypoint installs conservative, fail-closed
YAML workflow auditing and Kotlin Room-symbol hardening before delegating to that
implementation.

Security additions:
- reject escaped/encoded YAML mapping keys and ambiguous anchors/tags in mutable workflows;
- require permissions keys to use a canonical mapping-key form before permission auditing;
- parse uses references only from canonical block-style mapping entries;
- bind every retained legacy mutable-tag action to its exact audited occurrence/location,
  rather than trusting set membership by reference string;
- bind cross-file Room typealiases to their Kotlin package/import namespace so an unrelated
  same-basename declaration cannot be mistaken for a Room symbol in another package.
"""
from __future__ import annotations

import re
from typing import Any, Iterable

import phase109_guardian_v12_base as _base
from phase109_guardian_v12_base import *  # noqa: F401,F403

V3 = _base.V3
GuardianV12Error = _base.GuardianV12Error
require = _base.require
_BASE_SELF_TEST = _base.self_test
_BASE_VALIDATE_RUN = _base.validate_run
_BASE_VALIDATE_AND_PUBLISH = _base.validate_and_publish

_BLOCK_SCALAR = re.compile(r":\s*[|>][0-9+-]*\s*$")
_DOUBLE_QUOTED_KEY = re.compile(r'"((?:[^"\\]|\\.)*)"\s*:')
_USES_KEY = r"(?:uses|'uses'|\"uses\")"
_PERMISSION_KEY = r"(?:permissions|'permissions'|\"permissions\")"
_SCALAR_VALUE = r"(?:[^\s#'\"]+|'[^'\r\n]+'|\"[^\"\r\n]+\")"
_CANONICAL_USES = re.compile(
    rf"^\s*(?:-\s*)?{_USES_KEY}\s*:\s*({_SCALAR_VALUE})\s*$"
)
_ANY_USES_KEY = re.compile(rf"(?<![A-Za-z0-9_]){_USES_KEY}\s*:")
_CANONICAL_PERMISSION_KEY = re.compile(rf"^\s*{_PERMISSION_KEY}\s*:")
_ANY_PERMISSION_KEY = re.compile(rf"(?<![A-Za-z0-9_]){_PERMISSION_KEY}\s*:")
_KOTLIN_PACKAGE = re.compile(
    r"(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)\s*;?\s*$"
)
_KOTLIN_IMPORT = re.compile(
    r"(?m)^\s*import\s+"
    r"([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*(?:\.\*)?)"
    r"(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?\s*;?\s*$"
)
_ROOM_ALIAS_FQNS: set[str] = set()


def _yaml_security_lines(source: str) -> list[tuple[int, int, str]]:
    """Return non-scalar YAML code lines as (ordinal, physical_line, source_line).

    Block-scalar bodies (run: | / run: >) are excluded so shell/JSON content cannot
    be mistaken for YAML mapping syntax.
    """
    clean = V3.without_yaml_comments(source)
    result: list[tuple[int, int, str]] = []
    block_parent_indent: int | None = None
    ordinal = 0
    for physical, line in enumerate(clean.splitlines(), start=1):
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        if block_parent_indent is not None:
            if indent > block_parent_indent:
                continue
            block_parent_indent = None
        ordinal += 1
        result.append((ordinal, physical, line))
        if _BLOCK_SCALAR.search(line):
            block_parent_indent = indent
    return result


def _reject_ambiguous_yaml_features(source: str) -> None:
    for _, physical, line in _yaml_security_lines(source):
        # YAML decodes escapes inside double-quoted mapping keys. A textual regex
        # audit must never silently treat "u\\u0073es" as different from "uses".
        for match in _DOUBLE_QUOTED_KEY.finditer(line):
            if "\\" in match.group(1):
                raise GuardianV12Error(
                    f"Mutable workflow contains escaped/encoded YAML mapping key at line {physical}"
                )
        # Anchors, aliases, merge keys and explicit YAML tags can make the semantic
        # mapping differ from the literal mapping we audit. They are unnecessary in
        # trusted workflows and therefore fail closed.
        if re.search(r"(^|[\s{,\[])<<\s*:", line):
            raise GuardianV12Error(
                f"Mutable workflow contains YAML merge key at line {physical}"
            )
        if re.search(r"(^|[\s{,\[])[&*][A-Za-z_][A-Za-z0-9_-]*", line):
            raise GuardianV12Error(
                f"Mutable workflow contains YAML anchor/alias at line {physical}"
            )
        if "!!" in line:
            raise GuardianV12Error(
                f"Mutable workflow contains explicit YAML tag at line {physical}"
            )


def workflow_use_occurrences(source: str) -> list[tuple[int, int, str, str, str]]:
    """Return exact action occurrences with conservative source locations/context."""
    _reject_ambiguous_yaml_features(source)
    occurrences: list[tuple[int, int, str, str, str]] = []
    history: list[str] = []
    for ordinal, physical, line in _yaml_security_lines(source):
        match = _CANONICAL_USES.fullmatch(line)
        if match:
            previous_1 = history[-1] if history else ""
            previous_2 = history[-2] if len(history) >= 2 else ""
            indent = len(line) - len(line.lstrip(" "))
            occurrences.append(
                (ordinal, indent, previous_2, previous_1, V3.scalar(match.group(1)))
            )
        elif _ANY_USES_KEY.search(line):
            raise GuardianV12Error(
                "Mutable workflow contains unsupported uses syntax; "
                f"block-style scalar required at line {physical}: {line.strip()}"
            )
        history.append(line.strip())
    return occurrences


def workflow_uses(source: str) -> set[str]:
    return {occurrence[-1] for occurrence in workflow_use_occurrences(source)}


def validate_mutable_workflow_permissions(path: str, source: str) -> int:
    _reject_ambiguous_yaml_features(source)
    for _, physical, line in _yaml_security_lines(source):
        if _ANY_PERMISSION_KEY.search(line) and not _CANONICAL_PERMISSION_KEY.match(line):
            raise GuardianV12Error(
                f"Mutable workflow contains unsupported permissions key syntax at line "
                f"{physical}: {path}"
            )

    declarations = V3.parse_permission_declarations(source)
    top_level = [value for indent, value in declarations if indent == 0]
    require(
        len(top_level) == 1,
        f"Mutable workflow must have exactly one explicit top-level permissions declaration: {path}",
    )
    allowed_writes = _base.ALLOWED_WORKFLOW_WRITE_SCOPES.get(path, set())
    markers = 0
    for _, declaration in declarations:
        if isinstance(declaration, str):
            require(
                declaration in {"none", "read-all"},
                f"Mutable workflow has unsafe scalar permissions: {path}={declaration}",
            )
            markers += 1
            continue
        for scope, value in declaration.items():
            require(
                value in {"none", "read", "write"},
                f"Mutable workflow has invalid permission value: {path}:{scope}={value}",
            )
            if value == "write":
                require(
                    scope in allowed_writes,
                    f"Mutable workflow requests unauthorized write scope: {path}:{scope}",
                )
            markers += 1
    return markers


def _is_full_sha_action(ref: str) -> bool:
    return re.fullmatch(r"[^@\s]+@[0-9a-f]{40}", ref) is not None


def _validate_action_occurrences(
    path: str,
    base_occurrences: list[tuple[int, int, str, str, str]],
    candidate_occurrences: list[tuple[int, int, str, str, str]],
    base_pinned_refs: set[str],
) -> None:
    base_legacy_locations = {
        occurrence
        for occurrence in base_occurrences
        if not occurrence[-1].startswith("./") and not _is_full_sha_action(occurrence[-1])
    }
    for occurrence in candidate_occurrences:
        ref = occurrence[-1]
        if ref.startswith("./"):
            continue
        if _is_full_sha_action(ref):
            require(
                ref in base_pinned_refs,
                f"Mutable workflow action is not in trusted-base allowlist: {path}:{ref}",
            )
            continue
        # A mutable tag is tolerated only as grandfathered debt at the exact audited
        # occurrence. Duplicating or moving it changes this tuple and fails closed.
        require(
            occurrence in base_legacy_locations,
            f"Mutable workflow legacy action occurrence was added, duplicated, or moved: "
            f"{path}:{ref}",
        )


def audit_authorized_workflows(
    repo: str,
    token: str,
    base: str,
    head: str,
    paths: set[str],
    base_tree: dict[str, str],
    candidate_tree: dict[str, str],
) -> dict[str, Any]:
    base_pinned_refs: set[str] = set()
    base_occurrences_by_path: dict[str, list[tuple[int, int, str, str, str]]] = {}
    for path in sorted(V3.workflow_blobs(base_tree)):
        occurrences = workflow_use_occurrences(V3.fetch_text(repo, token, base, path))
        base_occurrences_by_path[path] = occurrences
        for occurrence in occurrences:
            ref = occurrence[-1]
            if ref.startswith("./"):
                continue
            if _is_full_sha_action(ref):
                base_pinned_refs.add(ref)

    audited = 0
    permission_markers = 0
    for path in sorted(paths):
        if not path.startswith(".github/workflows/") or path not in candidate_tree:
            continue
        source = V3.fetch_text(repo, token, head, path)
        permission_markers += validate_mutable_workflow_permissions(path, source)
        candidate_occurrences = workflow_use_occurrences(source)
        _validate_action_occurrences(
            path,
            base_occurrences_by_path.get(path, []),
            candidate_occurrences,
            base_pinned_refs,
        )
        audited += 1

    return {
        "mutableWorkflowsAudited": audited,
        "explicitPermissionMarkers": permission_markers,
        "trustedBasePinnedActionAllowlist": sorted(base_pinned_refs),
        "legacyActionOccurrencesBoundToLocation": True,
        "encodedYamlMappingKeysRejected": True,
    }


def _kotlin_package(clean_source: str) -> str:
    matches = _KOTLIN_PACKAGE.findall(clean_source)
    require(len(matches) <= 1, f"Ambiguous Kotlin package declarations: {matches}")
    return matches[0] if matches else ""


def _kotlin_imports(clean_source: str) -> tuple[dict[str, set[str]], set[str]]:
    exact: dict[str, set[str]] = {}
    stars: set[str] = set()
    for target, local_name in _KOTLIN_IMPORT.findall(clean_source):
        if target.endswith(".*"):
            require(not local_name, f"Kotlin wildcard import cannot be aliased: {target}")
            stars.add(target[:-2])
            continue
        local = local_name or target.rsplit(".", 1)[-1]
        exact.setdefault(local, set()).add(target)
    return exact, stars


def _alias_fqn(package_name: str, alias: str) -> str:
    return f"{package_name}.{alias}" if package_name else alias


def _reference_candidates(
    target: str,
    package_name: str,
    exact_imports: dict[str, set[str]],
    star_imports: set[str],
) -> set[str]:
    if "." in target:
        return {target}
    candidates = set(exact_imports.get(target, set()))
    candidates.add(_alias_fqn(package_name, target))
    candidates.update(f"{package}.{target}" for package in star_imports)
    return candidates


def resolve_global_room_typealiases(clean_sources: Iterable[str]) -> set[str]:
    """Resolve Room typealiases by fully-qualified declaration identity.

    The legacy V12 API returns simple alias names, so that return shape is preserved for
    compatibility. Internally, however, visibility is tracked by fully-qualified names; a
    `SharedRoom` declared in one package is never exposed as `SharedRoom` in an unrelated
    package unless Kotlin import/package rules make that exact declaration visible there.
    """
    definitions: list[
        tuple[str, str, str, dict[str, set[str]], set[str], set[str]]
    ] = []
    for clean in clean_sources:
        package_name = _kotlin_package(clean)
        exact_imports, star_imports = _kotlin_imports(clean)
        local_room_symbols = set(_base.BASE_V11_KOTLIN_ROOM_SYMBOLS(clean))
        for alias, target in _base.typealias_pairs(clean):
            definitions.append(
                (
                    _alias_fqn(package_name, alias),
                    target,
                    package_name,
                    exact_imports,
                    star_imports,
                    local_room_symbols,
                )
            )

    resolved: set[str] = set()
    for alias_fqn, target, _, _, _, local_room_symbols in definitions:
        if target == "androidx.room.Room" or target in local_room_symbols:
            resolved.add(alias_fqn)

    changed = True
    while changed:
        changed = False
        for alias_fqn, target, package_name, exact_imports, star_imports, _ in definitions:
            if alias_fqn in resolved:
                continue
            candidates = _reference_candidates(
                target, package_name, exact_imports, star_imports
            )
            if candidates & resolved:
                resolved.add(alias_fqn)
                changed = True

    _ROOM_ALIAS_FQNS.clear()
    _ROOM_ALIAS_FQNS.update(resolved)
    return {fqn.rsplit(".", 1)[-1] for fqn in resolved}


def imported_room_typealias_names(clean_source: str, global_aliases: set[str]) -> set[str]:
    """Return only Room typealias names actually visible under Kotlin namespace rules."""
    del global_aliases  # compatibility argument; FQNs are the authoritative internal index.
    package_name = _kotlin_package(clean_source)
    exact_imports, star_imports = _kotlin_imports(clean_source)
    visible: set[str] = set()

    for fqn in _ROOM_ALIAS_FQNS:
        if "." in fqn:
            alias_package, alias_name = fqn.rsplit(".", 1)
        else:
            alias_package, alias_name = "", fqn
        if alias_package == package_name:
            visible.add(alias_name)
        if alias_package in star_imports:
            visible.add(alias_name)

    for local_name, targets in exact_imports.items():
        if targets & _ROOM_ALIAS_FQNS:
            visible.add(local_name)
    return visible


def kotlin_room_symbols_with_aliases(clean_source: str, global_aliases: set[str]) -> set[str]:
    # Fully-qualified aliases are always safe to recognize. Simple names are added only when the
    # declaration is visible in this source file through same-package, exact-import, aliased-import,
    # or wildcard-import rules. This removes the former repository-global basename exposure.
    return (
        set(_base.BASE_V11_KOTLIN_ROOM_SYMBOLS(clean_source))
        | set(_ROOM_ALIAS_FQNS)
        | imported_room_typealias_names(clean_source, global_aliases)
    )


def validate_run(root, repo: str, token: str, run_id: int, head: str) -> dict[str, Any]:
    result = _BASE_VALIDATE_RUN(root, repo, token, run_id, head)
    contract = result.get("productionRoomBuilderContractV12")
    if isinstance(contract, dict):
        contract["roomTypealiasNamespacesBound"] = True
        contract["unrelatedSameBasenameAliasesExcluded"] = True
    return result


def validate_and_publish(root, repo: str, token: str, run_id: int, head: str,
                         target_url: str) -> dict[str, Any]:
    """Run the base publication flow while forcing its temporary v11 hook to this validator.

    The preserved base publisher temporarily assigns `_base.validate_run` into `v11.validate_run`.
    Keeping `_base.validate_run` pointed at this wrapper ensures repeated calls do not silently
    fall back to the pre-namespace validator when the base publisher restores its hook.
    """
    old_base_validate = _base.validate_run
    _base.validate_run = validate_run
    try:
        return _BASE_VALIDATE_AND_PUBLISH(root, repo, token, run_id, head, target_url)
    finally:
        _base.validate_run = old_base_validate


def _room_alias_namespace_self_test() -> None:
    executable_kotlin = _base.v11.v10.v9.v8.v7.v6.v5.v4.executable_kotlin
    trusted = executable_kotlin(
        """
        package trusted.room
        import androidx.room.Room
        typealias SharedRoom = Room
        """
    )
    decoy = executable_kotlin(
        """
        package decoy
        typealias SharedRoom = java.lang.String
        val decoyBuilder = SharedRoom.databaseBuilder(ctx, Db::class.java, "decoy").build()
        """
    )
    chained = executable_kotlin(
        """
        package chained
        import trusted.room.SharedRoom as ImportedRoom
        typealias ChainedRoom = ImportedRoom
        val chainedBuilder = ChainedRoom.databaseBuilder(ctx, Db::class.java, "chain").build()
        """
    )
    exact_consumer = executable_kotlin(
        """
        package exactconsumer
        import trusted.room.SharedRoom
        val exactBuilder = SharedRoom.databaseBuilder(ctx, Db::class.java, "exact").build()
        """
    )
    wildcard_consumer = executable_kotlin(
        """
        package wildcardconsumer
        import trusted.room.*
        val wildcardBuilder = SharedRoom.databaseBuilder(ctx, Db::class.java, "wildcard").build()
        """
    )
    wrong_consumer = executable_kotlin(
        """
        package wrongconsumer
        import decoy.SharedRoom
        val wrongBuilder = SharedRoom.databaseBuilder(ctx, Db::class.java, "wrong").build()
        """
    )
    unrelated_consumer = executable_kotlin(
        """
        package unrelated
        val unrelatedBuilder = SharedRoom.databaseBuilder(ctx, Db::class.java, "none").build()
        """
    )

    aliases = resolve_global_room_typealiases((trusted, decoy, chained))
    require(aliases == {"SharedRoom", "ChainedRoom"},
            f"Package-aware Room alias resolution changed compatibility result: {aliases}")
    require("trusted.room.SharedRoom" in _ROOM_ALIAS_FQNS,
            "Fully-qualified direct Room typealias was not indexed")
    require("chained.ChainedRoom" in _ROOM_ALIAS_FQNS,
            "Cross-package imported Room typealias chain was not resolved")
    require("decoy.SharedRoom" not in _ROOM_ALIAS_FQNS,
            "Non-Room same-basename typealias was incorrectly resolved")

    decoy_symbols = kotlin_room_symbols_with_aliases(decoy, aliases)
    require("SharedRoom" not in decoy_symbols,
            "Unrelated same-basename declaration leaked into Room symbols")
    require(len(_base.v11.find_builder_chains(decoy, decoy_symbols)) == 0,
            "Decoy same-basename builder was incorrectly treated as Room")

    wrong_symbols = kotlin_room_symbols_with_aliases(wrong_consumer, aliases)
    require("SharedRoom" not in wrong_symbols,
            "Wrong-package exact import was incorrectly treated as Room")
    require(len(_base.v11.find_builder_chains(wrong_consumer, wrong_symbols)) == 0,
            "Wrong-package imported builder was incorrectly treated as Room")

    unrelated_symbols = kotlin_room_symbols_with_aliases(unrelated_consumer, aliases)
    require("SharedRoom" not in unrelated_symbols,
            "Unimported Room typealias basename leaked across packages")
    require(len(_base.v11.find_builder_chains(unrelated_consumer, unrelated_symbols)) == 0,
            "Unimported unrelated builder was incorrectly treated as Room")

    exact_symbols = kotlin_room_symbols_with_aliases(exact_consumer, aliases)
    require("SharedRoom" in exact_symbols,
            "Exact imported Room typealias was not visible")
    require(len(_base.v11.find_builder_chains(exact_consumer, exact_symbols)) == 1,
            "Exact imported Room typealias builder was not detected")

    wildcard_symbols = kotlin_room_symbols_with_aliases(wildcard_consumer, aliases)
    require("SharedRoom" in wildcard_symbols,
            "Wildcard imported Room typealias was not visible")
    require(len(_base.v11.find_builder_chains(wildcard_consumer, wildcard_symbols)) == 1,
            "Wildcard imported Room typealias builder was not detected")

    chained_symbols = kotlin_room_symbols_with_aliases(chained, aliases)
    require("ImportedRoom" in chained_symbols and "ChainedRoom" in chained_symbols,
            "Aliased import or same-package chained Room alias was not visible")
    require(len(_base.v11.find_builder_chains(chained, chained_symbols)) == 1,
            "Cross-package chained Room typealias builder was not detected")


def _expect_rejected(callable_obj: Any, message: str) -> None:
    try:
        callable_obj()
    except GuardianV12Error:
        return
    raise GuardianV12Error(message)


def self_test() -> dict[str, Any]:
    result = _BASE_SELF_TEST()
    _room_alias_namespace_self_test()

    encoded_uses = (
        "permissions:\n  contents: read\njobs:\n  test:\n    steps:\n"
        '      - "u\\u0073es": evil/action@main\n'
    )
    _expect_rejected(
        lambda: workflow_uses(encoded_uses),
        "Encoded YAML uses key unexpectedly passed trusted workflow audit",
    )

    encoded_permissions = (
        "permissions:\n  contents: read\njobs:\n  test:\n"
        '    "per\\u006dissions": { contents: write }\n'
        "    steps:\n      - run: echo ok\n"
    )
    _expect_rejected(
        lambda: validate_mutable_workflow_permissions(
            ".github/workflows/example.yml", encoded_permissions
        ),
        "Encoded YAML permissions key unexpectedly passed trusted workflow audit",
    )

    legacy_base = (
        "permissions:\n  contents: read\njobs:\n  test:\n    steps:\n"
        "      - name: Checkout\n        uses: actions/checkout@v6\n"
    )
    legacy_moved = (
        "permissions:\n  contents: read\njobs:\n  test:\n    steps:\n"
        "      - name: Prep\n        run: echo ok\n"
        "      - name: Checkout\n        uses: actions/checkout@v6\n"
    )
    base_occurrences = workflow_use_occurrences(legacy_base)
    _validate_action_occurrences(
        ".github/workflows/example.yml",
        base_occurrences,
        workflow_use_occurrences(legacy_base),
        set(),
    )
    _expect_rejected(
        lambda: _validate_action_occurrences(
            ".github/workflows/example.yml",
            base_occurrences,
            workflow_use_occurrences(legacy_moved),
            set(),
        ),
        "Moved legacy mutable-tag action unexpectedly passed trusted workflow audit",
    )

    legacy_duplicate = legacy_base.replace(
        "        uses: actions/checkout@v6\n",
        "        uses: actions/checkout@v6\n"
        "      - name: Duplicate\n        uses: actions/checkout@v6\n",
    )
    _expect_rejected(
        lambda: _validate_action_occurrences(
            ".github/workflows/example.yml",
            base_occurrences,
            workflow_use_occurrences(legacy_duplicate),
            set(),
        ),
        "Duplicated legacy mutable-tag action unexpectedly passed trusted workflow audit",
    )

    evolution = dict(result.get("trustedCiEvolutionAuthorizationV1", {}))
    evolution.update(
        {
            "encodedYamlUsesKeysRejected": True,
            "encodedYamlPermissionKeysRejected": True,
            "yamlAnchorsAliasesAndTagsRejected": True,
            "legacyActionOccurrencesBoundToLocation": True,
            "legacyActionDuplicationRejected": True,
            "roomTypealiasNamespacesBound": True,
            "unrelatedSameBasenameRoomAliasesExcluded": True,
            "exactAliasedAndWildcardRoomImportsResolved": True,
            "namespaceValidatorReentrantAcrossPublish": True,
        }
    )
    result["trustedCiEvolutionAuthorizationV1"] = evolution
    return result


def _install_hardening() -> None:
    _base.workflow_use_occurrences = workflow_use_occurrences
    _base.workflow_uses = workflow_uses
    _base.validate_mutable_workflow_permissions = validate_mutable_workflow_permissions
    _base.audit_authorized_workflows = audit_authorized_workflows
    _base.resolve_global_room_typealiases = resolve_global_room_typealiases
    _base.imported_room_typealias_names = imported_room_typealias_names
    _base.kotlin_room_symbols_with_aliases = kotlin_room_symbols_with_aliases
    _base.validate_run = validate_run
    _base.validate_and_publish = validate_and_publish
    _base.self_test = self_test


_install_hardening()


def main() -> int:
    _install_hardening()
    return _base.main()


if __name__ == "__main__":
    raise SystemExit(main())