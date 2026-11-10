#!/usr/bin/env python3
"""Create Web Push key settings in a new private file."""
import argparse
import base64
import os
import re
import subprocess
from urllib.parse import urlsplit


def openssl(*arguments, data=None):
    return subprocess.run(
        ["openssl", *arguments], input=data, capture_output=True, check=True
    ).stdout


def encode(value):
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("subject", help="Public HTTPS contact URL or mailto:address")
    parser.add_argument("output", help="New environment file, outside version control")
    args = parser.parse_args()
    uri = urlsplit(args.subject)
    if not (
        re.fullmatch(r"mailto:[^@\s'\"$]+@[^@\s'\"$]+", args.subject)
        or (uri.scheme == "https" and uri.hostname and not uri.username
            and not uri.query and not uri.fragment and re.fullmatch(r"https://[A-Za-z0-9./_~:%-]+", args.subject))
    ):
        parser.error("subject must be an HTTPS URL or mailto address")
    key = openssl("ecparam", "-name", "prime256v1", "-genkey", "-noout")
    public = openssl("pkey", "-pubout", "-outform", "DER", data=key)[-65:]
    private = openssl("pkcs8", "-topk8", "-nocrypt", "-outform", "DER", data=key)
    with os.fdopen(os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as output:
        output.write(f"PUSH_SUBJECT='{args.subject}'\nPUSH_PUBLIC_KEY='{encode(public)}'\nPUSH_PRIVATE_KEY='{encode(private)}'\n")
    print("Created a private Web Push environment file.")


if __name__ == "__main__":
    main()
