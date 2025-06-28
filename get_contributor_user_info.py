import os
import json
import requests
from pathlib import Path
from time import sleep
from PIL import Image
from io import BytesIO
from typing import Optional
import time

GITHUB_USERS = [
    "nift4",
    "123Duo3",
    "AkaneTan",
    "imjyotiraditya",
    "WSTxda",
    "pxeemo",
    "Lambada10",
    "lightsummer233",
    "nicholaswww",
    "Yuyuko1024",
    "ghhccghk",
    "lucaxvi",
    "tungnk123",
    "topazrn",
    "strongville",
    "bggRGjQaUbCoE",
    "VishnuSanal",
    "mikooomich",
    "HotarunIchijou",
    "someone5678",
    "IzzySoft",
    "PalanixYT",
    "N3Shemmy3",
    # add login ID ...
]


DRAWABLE_DIR = "app/src/main/res/drawable"
OUTPUT_JSON = "app/src/main/assets/github_cache_output.json"
API_BASE = "https://api.github.com/users/"

HEADERS = {
    "Accept": "application/vnd.github+json",
    # "Authorization": "Bearer <your_token>"  # Add a GitHub Token if requested frequently
}

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

def download_and_save_avatar(url: str, filename: str) -> bool:
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        img = Image.open(BytesIO(response.content)).convert("RGBA")
        os.makedirs(DRAWABLE_DIR, exist_ok=True)
        filepath = os.path.join(DRAWABLE_DIR, f"{filename}.png")
        img.save(filepath, format="PNG")
        print(f"✅ download ok: {filepath}")
        return True
    except Exception as e:
        print(f"❌ download faile : {url} -> {e}")
        return False

def main():
    result = {}

    for login in GITHUB_USERS:
        print(f"📦 Processing users: {login}")
        user_data = fetch_user_data(login)
        if not user_data:
            continue

        filename = f"contributor_{sanitize_login(login)}"
        avatar_url = user_data.get("avatar_url", "")
        if download_and_save_avatar(avatar_url, filename):
            result[login] = {
                "timestamp": int(time.time() * 1000),
                "user": {
                    "avatar_url": f"@drawable/{filename}",
                    "bio": user_data.get("bio", ""),
                    "contribute": "",
                    "login": login,
                    "name": user_data.get("name", "")
                }
            }

        sleep(0.5)

    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(f"\n✅ All user processing is complete and results have been saved to {OUTPUT_JSON}")

if __name__ == "__main__":
    main()
