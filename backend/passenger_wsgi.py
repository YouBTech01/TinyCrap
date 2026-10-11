"""cPanel Application Manager / Passenger entry point.

Passenger looks for a module-level ``application``. server.py stays the real
entry point; this file only re-exports it.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from server import application  # noqa: E402,F401
