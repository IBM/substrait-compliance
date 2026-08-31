#!/usr/bin/env python3
"""
No-cache HTTP server for the Substrait Compliance Dashboard.
Adds Cache-Control: no-store to every response so browsers always
fetch fresh JSON data after running the demo scripts.

Usage:
    python3 serve.py          # serves on port 8080
    python3 serve.py 8081     # serves on a custom port
"""

import sys
import http.server
import functools

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate")
        self.send_header("Pragma", "no-cache")
        super().end_headers()

    def log_message(self, fmt, *args):
        # Suppress per-request noise; only show startup message.
        pass


if __name__ == "__main__":
    with http.server.HTTPServer(("", PORT), NoCacheHandler) as httpd:
        print(f"Dashboard: http://localhost:{PORT}/")
        print(f"  TPC-H    -> http://localhost:{PORT}/index.html")
        print(f"  TPC-DS   -> http://localhost:{PORT}/tpcds-tests.html")
        print(f"  Functions-> http://localhost:{PORT}/function-tests.html")
        print("Press Ctrl+C to stop.")
        httpd.serve_forever()
