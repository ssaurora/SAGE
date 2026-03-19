from __future__ import annotations

import os
from pathlib import Path


DEFAULT_WORKSPACE_ROOT = Path("/workspace")
WORKSPACE_SUBDIRS = ("input", "output", "logs")


def resolve_workspace_root() -> Path:
    workspace_root = os.getenv("WORKSPACE_ROOT")
    if workspace_root:
        return Path(workspace_root)
    return DEFAULT_WORKSPACE_ROOT


def ensure_workspace_directories() -> None:
    root = resolve_workspace_root()
    root.mkdir(parents=True, exist_ok=True)
    for subdir in WORKSPACE_SUBDIRS:
        (root / subdir).mkdir(parents=True, exist_ok=True)

