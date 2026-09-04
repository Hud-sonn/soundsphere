"""Docs router — serves HTML renderings of the repo's Markdown docs.

All docs are viewable only from the backend domain at /docs (and /docs/{name}.html).
They are generated from the MDs with a Date/Status/Why header per AGENTS.md rule,
and linked together via the index at /docs. Not bundled in the APK.
"""
from fastapi import APIRouter
from fastapi.responses import HTMLResponse
from fastapi.staticfiles import StaticFiles
import os

router = APIRouter(prefix="/docs")

# This router is mounted as StaticFiles in main.py — see _docs_dir mount.
# Keeping the APIRouter for future /docs/search or /docs/api if needed.

_docs_dir = os.path.join(os.path.dirname(__file__), "..", "docs")
