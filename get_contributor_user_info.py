import os
import sys
import requests
from pathlib import Path
from time import sleep
from PIL import Image
from io import BytesIO
from typing import Optional
import time
import urllib.parse

# Tuple of GitHub login ID to string resource ID
# Use following command to get a rough idea who should be on this list:
# git shortlog -n -s -- ':!app/src/main/res/values*/strings.xml' ':!fastlane/metadata/android/' ':!readme*.md'
GITHUB_USERS = [
    ("nift4", "contributors_nift4"),
    ("123Duo3", "contributors_123duo3"),
    ("AkaneTan", "contributors_akanetan"),
    ("imjyotiraditya", "contributors_developer"),
    ("WSTxda", "contributors_wstxda"),
    ("pxeemo", "contributors_pxeemo"),
    ("Lambada10", "contributors_lambada10"),
    ("lightsummer233", "contributors_developer"),
    ("nicholaswww", "contributors_code_contributions"),
    ("Yuyuko1024", "contributors_yuyuko1024"),
    ("ghhccghk", "contributors_ghhccghk"),
    ("mikooomich", "contributors_code_contributions"),
    ("lucaxvi", "contributors_code_contributions"),
    ("tungnk123", "contributors_code_contributions"),
    ("topazrn", "contributors_code_contributions"),
    ("strongville", "contributors_code_contributions"),
    ("SurFace81", "contributors_code_contributions"),
    ("bggRGjQaUbCoE", "contributors_code_contributions"),
    ("VishnuSanal", "contributors_code_contributions"),
    ("HotarunIchijou", "contributors_code_contributions"),
    ("someone5678", "contributors_code_contributions"),
    ("PalanixYT", "contributors_code_contributions"),
    ("N3Shemmy3", "contributors_code_contributions"),
    ("SharnavM", "contributors_code_contributions"),
]

# TODO: delete old webp files so that webp file is removed if someone is removed from credits

DRAWABLE_DIR = "app/src/main/res/drawable"
OUTPUT_KT = "app/src/main/java/org/akanework/gramophone/logic/utils/data/Contributors.kt"
API_BASE = "https://api.github.com/users/"
WEBLATE_CREDITS = "https://hosted.weblate.org/api/projects/gramophone/credits/?start=1970-01-01T00:00:00Z&end=2099-01-01T00:00:00Z"
REPO = "FoedusProgramme/Gramophone"
REPO_URL = f"https://api.github.com/repos/{REPO}/contributors?per_page=100"

HEADERS = {
    "Accept": "application/vnd.github+json",
}
WEBLATE_HEADERS = {
    "Accept": "application/json",
}

try:
    with open("fastlane/creds.txt", "r", encoding="utf-8") as f:
        HEADERS["Authorization"] = "Bearer " + f.read().strip()
except Exception as e:
    print("Not using auth, may be subject to rate limits")

try:
    with open("fastlane/weblate.txt", "r", encoding="utf-8") as f:
        WEBLATE_HEADERS["Authorization"] = "Bearer " + f.read().strip()
except Exception as e:
    print("Missing weblate API key")
    sys.exit(1)

def sanitize_login(login: str) -> str:
    return ''.join(c if c.isalnum() else '_' for c in login.lower())

def fetch_user_data(login: str) -> Optional[dict]:
    url = f"{API_BASE}{login}"
    try:
        response = requests.get(url, headers=HEADERS, timeout=10)
        response.raise_for_status()
        return response.json()
    except Exception as e:
        print(f"❌ get users error {login}: {e}")
        return None

def fetch_translation_credits() -> Optional[dict]:
    try:
        response = requests.get(WEBLATE_CREDITS, headers=WEBLATE_HEADERS, timeout=10)
        response.raise_for_status()
        return response.json()
    except Exception as e:
        print(f"❌ get credits error: {e}")
        return None

def download_and_save_avatar(url: str, filename: str):
    response = requests.get(url, timeout=10)
    response.raise_for_status()
    img = Image.open(BytesIO(response.content)).convert("RGBA")
    img.thumbnail((128, 128))
    os.makedirs(DRAWABLE_DIR, exist_ok=True)
    filepath = os.path.join(DRAWABLE_DIR, f"{filename}.webp")
    img.save(filepath, format="WebP", quality=50, method=6)
    print(f"✅ download ok: {filepath}")

def fetch_contributors():
    print("📦 Fetching GitHub contributors list…")

    page = 1
    all_contributors = []
    existing_users = {login.lower() for login, _ in GITHUB_USERS}

    while True:
        url = f"{REPO_URL}&page={page}"
        response = requests.get(url, headers=HEADERS, timeout=10)
        if response.status_code != 200:
            print(f"❌ Error fetching contributors (page {page}): {response.status_code} - {response.text}")
            break

        contributors = response.json()
        if not contributors:
            break

        all_contributors.extend(contributors)
        print(f"📄 Loaded page {page}, got {len(contributors)} contributors.")
        page += 1
        time.sleep(0.5)

    print(f"📦 Total contributors fetched: {len(all_contributors)}")

    filtered = []
    for user in all_contributors:
        login = user.get("login", "").lower()
        user_type = user.get("type", "").lower()
        if login in existing_users:
            print(f"Skip existing user: {login}")
            continue

        # Skip bot
        if user_type != "bot" and login not in ["weblate"]:
            print(f"Skip bot: {login}")
            continue

        filtered.append(login)

    if not filtered:
        print("No new contributors found.")
        return

    print(f"\n Found {len(filtered)} new human contributors, appending to GITHUB_USERS:")

    for login in filtered:
        print(f"    + {login}")
        GITHUB_USERS.append((login, "contributors_code_contributions"))

    print(f" GITHUB_USERS now contains {len(GITHUB_USERS)} entries.\n")

def main():
    fetch_contributors()
    result = """// ===== DO NOT EDIT, CHANGES WILL BE OVERWRITTEN =====
// automatically generated by get_contributor_user_info.py

package org.akanework.gramophone.logic.utils.data

import android.net.Uri
import org.akanework.gramophone.R

object Contributors {
    private fun decode(text: String?) = text?.let { Uri.decode(it) }
    val LIST = listOf("""

    for user in GITHUB_USERS:
        login = user[0]
        print(f"📦 Processing users: {login}")
        user_data = fetch_user_data(login)
        if not user_data:
            return

        filename = f"contributor_{sanitize_login(login)}"
        avatar_url = user_data.get("avatar_url", "")
        name = ("\"" + urllib.parse.quote(user_data["name"]) + "\"") if ("name" in user_data and user_data["name"]) else "null"
        download_and_save_avatar(avatar_url, filename)
        result += f"\n        GitHubUser(login = \"{login}\", name = decode({name}), avatar = R.drawable.{filename}, contributed = R.string.{user[1]}),"

    result += "\n    )\n"
    print("📦 Fetching credits from Weblate")
    credits = fetch_translation_credits()
    if not credits:
        return
    names = set()
    for language in credits:
        for users in language.values():
            for user in users:
                names.add(user["full_name"])
    result += "    val TRANSLATORS = listOf("
    names = sorted(names)
    for name in names:
        result += "\n        decode(\"" + urllib.parse.quote(name) + "\"),"
    result += "\n    )\n}\n"
    with open(OUTPUT_KT, "w", encoding="utf-8") as f:
        f.write(result)

    print(f"\n✅ All user processing is complete and results have been saved to {OUTPUT_KT}")

if __name__ == "__main__":
    main()
