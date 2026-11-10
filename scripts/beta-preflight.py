#!/usr/bin/env python3
"""Check the anonymous surface of a Dot Home beta deployment."""

import argparse
from http.cookies import SimpleCookie
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class PreflightError(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def require(condition, message):
    if not condition:
        raise PreflightError(message)


def check(origin, target):
    http = build_opener(NoRedirect())

    def request(path, expected=200, method="GET"):
        try:
            response = http.open(Request(target + path, method=method), timeout=10)
        except HTTPError as error:
            response = error
        except (URLError, TimeoutError) as error:
            raise PreflightError(f"{path}: connection failed") from error
        with response:
            require(response.code == expected, f"{path}: expected HTTP {expected}, got {response.code}")
            return response.headers, response.read()

    headers, body = request("/healthz")
    require(json.loads(body) == {"status": "UP"} and "no-store" in headers.get("Cache-Control", ""),
            "/healthz: expected an uncached readiness response")
    token = "a" * 43
    for path in ("/", "/login", "/onboarding", "/homes", "/settings", "/home/example",
                 f"/invite/{token}", f"/visit/{token}"):
        headers, body = request(path)
        require(b'<div id="root">' in body and "no-store" in headers.get("Cache-Control", ""),
                f"{path}: expected the uncached SPA")

    headers, body = request("/push-worker.js")
    require("javascript" in headers.get_content_type() and b'"notificationclick"' in body and
            "no-cache" in headers.get("Cache-Control", ""), "/push-worker.js: expected a revalidated JavaScript worker")
    headers, body = request("/manifest.webmanifest")
    manifest = json.loads(body)
    require(manifest.get("start_url") == "/" and manifest.get("scope") == "/" and
            "no-cache" in headers.get("Cache-Control", ""), "/manifest.webmanifest: invalid scope or cache policy")
    headers, _ = request("/art/app-icon.svg")
    require(headers.get_content_type() == "image/svg+xml", "/art/app-icon.svg: expected an SVG icon")

    _, body = request("/api/auth/config")
    require(json.loads(body) == {"googleEnabled": True, "devEnabled": False},
            "/api/auth/config: expected Google enabled and dev disabled")
    headers, _ = request("/api/auth/csrf")
    cookies = SimpleCookie()
    for value in headers.get_all("Set-Cookie", []):
        cookies.load(value)
    session = cookies.get("SESSION")
    require(session and session["secure"] and session["httponly"] and session["samesite"].lower() == "lax",
            "/api/auth/csrf: session cookie must be Secure, HttpOnly and SameSite=Lax")
    require(headers.get("Referrer-Policy") == "no-referrer" and headers.get("Strict-Transport-Security"),
            "/api/auth/csrf: expected no-referrer and HSTS")

    headers, _ = request("/oauth2/authorization/google", 302)
    location = urlsplit(headers.get("Location", ""))
    require(location.scheme == "https" and location.netloc == "accounts.google.com" and
            parse_qs(location.query).get("redirect_uri") == [origin + "/login/oauth2/code/google"],
            "/oauth2/authorization/google: unexpected provider or callback")
    for path in ("/api/me", "/api/me/browser-push", "/api/me/former-records/notes"):
        request(path, 401)
    request("/api/auth/dev", 403, "POST")
    request("/assets/missing.js", 404)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("origin", help="Deployment HTTPS origin without a path")
    parser.add_argument("--http-port", type=int, help="Connect to this loopback HTTP port for a local production-image test")
    args = parser.parse_args()
    origin = args.origin
    try:
        url = urlsplit(origin)
        require(url.scheme == "https" and url.hostname and not url.username and not url.password and
                origin == f"https://{url.netloc}", "Provide the HTTPS origin without a trailing slash, path or query")
        if args.http_port is not None:
            require(1 <= args.http_port <= 65535, "Invalid loopback HTTP port")
        target = origin if args.http_port is None else f"http://127.0.0.1:{args.http_port}"
        check(origin, target)
    except PreflightError as error:
        print(f"FAIL: {error}", file=sys.stderr)
        return 1
    except (ValueError, AttributeError):
        print("FAIL: unexpected deployment response or origin", file=sys.stderr)
        return 1
    print("PASS: beta readiness, SPA routes, push assets, Google callback and anonymous access controls")
    return 0


if __name__ == "__main__":
    sys.exit(main())
