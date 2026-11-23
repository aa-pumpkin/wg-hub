#!/usr/bin/env python3
"""Drives the app in the emulator for checks: tap by text, type, take screenshots.

    python3 scripts/phone.py tap "Log in"        tap the element showing this text
    python3 scripts/phone.py type "anna"          type into the focused field
    python3 scripts/phone.py shot 02-chores       save shots/02-chores.png
    python3 scripts/phone.py texts                list the texts on screen
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ADB = str(ROOT.parent / ".tools/android-sdk/platform-tools/adb")


def adb(*args, **kw):
    return subprocess.run([ADB, *args], check=True, capture_output=True, **kw).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    tree = ET.fromstring(adb("shell", "cat", "/sdcard/ui.xml"))
    for node in tree.iter("node"):
        label = node.get("text") or node.get("content-desc") or ""
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds") or ""))
        yield label, ((x1 + x2) // 2, (y1 + y2) // 2)


def tap(text, index=0):
    for _ in range(10):
        matches = [pos for label, pos in nodes() if label == text] or \
                  [pos for label, pos in nodes() if text in label]
        if len(matches) > index:
            x, y = matches[index]
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(1)
            return
        time.sleep(1)
    sys.exit(f"not found: {text}")


def main():
    command, *rest = sys.argv[1:]
    if command == "tap":
        tap(rest[0], int(rest[1]) if len(rest) > 1 else 0)
    elif command == "type":
        adb("shell", "input", "text", rest[0].replace(" ", "%s"))
    elif command == "clear":
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        adb("shell", "input", "keyevent", *["KEYCODE_DEL"] * 60)
    elif command == "key":
        adb("shell", "input", "keyevent", rest[0])
    elif command == "swipe":
        adb("shell", "input", "swipe", *rest)
    elif command == "shot":
        (ROOT / "shots").mkdir(exist_ok=True)
        (ROOT / "shots" / f"{rest[0]}.png").write_bytes(adb("exec-out", "screencap", "-p"))
    elif command == "texts":
        for label, pos in nodes():
            if label:
                print(pos, label)


if __name__ == "__main__":
    main()
