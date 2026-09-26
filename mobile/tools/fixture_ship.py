#!/usr/bin/env python3
"""Loopback-only, ephemeral Eyre fixture for native-client QA; not an Urbit emulator.

Run: python3 tools/fixture_ship.py
Android emulator URL: http://10.0.2.2:18787, +code: zod (or nec for a second account).
No real credentials, production ship data, or Gall implementation is involved.
"""
import argparse
import copy
import datetime as dt
import json
import queue
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs


def reminder(identifier, title, **extra):
    return dict(id=identifier, title=title, notes="", url=None, priority="none",
                flagged=False, tags=[], rank=identifier * 1024, assignee="~zod",
                schedule=None, completed=False, revision=1,
                **{"parent-id": None, "section-id": None}) | extra


def initial(ship):
    today = dt.datetime.now(dt.timezone.utc)
    due = f"~{today.year}.{today.month}.{today.day}..18.30.00"
    return {"id": 1, "title": "A little everyday" if ship == "~zod" else "Nec's private list",
            "color": "#7d9a7a", "symbol": "", "revision": 1,
            "sections": [{"id": 1, "title": "Make some space", "rank": 1024}],
            "reminders": [
                reminder(1, "A second thing", **{"section-id": 1}),
                reminder(2, "A small step underneath", **{"parent-id": 1, "section-id": 1}),
                reminder(3, "And one more little step", **{"parent-id": 2, "section-id": 1}),
                reminder(4, "Water the plants", flagged=True, tags=["home"], schedule={
                    "due-at": due, "timezone": "UTC", "all-day": False, "early-seconds": [],
                    "occurrence": 0, "recurrence": {"frequency": "daily", "interval": 1,
                    "weekdays": [], "month-days": [], "month-week": None,
                    "end-at": None, "max-occurrences": None}}),
                reminder(5, "Read a chapter before bed", notes="A quieter way to finish the day.")
            ]}


class Fixture:
    def __init__(self):
        self.lock = threading.RLock()
        self.states = {ship: [initial(ship)] for ship in ("~zod", "~nec")}
        self.receipts = {}
        self.channels = {}
        self.events = 0

    def accesses(self, ship):
        return {"accesses": [{"alias": l["id"], "host": ship, "host-list-id": l["id"],
                              "status": "online", "owner": True,
                              "members": [{"ship": "~bus", "policy": {}}], "pending": []}
                             for l in self.states[ship]]}

    def snapshot(self, ship):
        return {"snapshot": {"protocol-version": 2, "lists": self.states[ship],
                             "preferences": {"revision": 1, "default-list": 1,
                                             "all-day-minute": 540}, "snoozes": []}}

    def event(self, channel, response, **fields):
        self.events += 1
        channel["queue"].put((self.events, copy.deepcopy(dict(id=1, response=response, **fields))))

    def broadcast(self, ship, fact):
        for channel in list(self.channels.values()):
            if channel["ship"] == ship and channel["subscribed"]:
                self.event(channel, "diff", json=fact)

    def mutate(self, ship, action):
        kind, body = next(iter(action.items()))
        op = body["operation-id"]
        key = (ship, op)
        if key in self.receipts:
            return
        lists = self.states[ship]
        target = next((x for x in lists if x["id"] == body.get("list-id")), None)
        if target and body.get("base-revision", target["revision"]) != target["revision"]:
            result = {"rejected": {"operation-id": op, "reason": "stale revision",
                                   "current-revision": target["revision"]}}
        else:
            if kind == "create-list":
                target = dict(id=max((x["id"] for x in lists), default=0) + 1,
                              title=body["title"], revision=0, sections=[], reminders=[],
                              color="#c5ccd6", symbol="")
                lists.append(target)
            elif kind == "delete-list":
                lists.remove(target)
            elif kind == "update-list":
                target.update({k: body[k] for k in ("title", "color", "symbol")})
            elif kind == "add-section":
                target["sections"].append(dict(id=max((s["id"] for s in target["sections"]), default=0)+1,
                                               title=body["title"], rank=body["rank"]))
            elif kind == "add-reminder":
                identifier = max((r["id"] for r in target["reminders"]), default=0)+1
                target["reminders"].append(reminder(identifier, body["title"], assignee=None))
            else:
                r = next(r for r in target["reminders"] if r["id"] == body["reminder-id"])
                if kind == "update-reminder":
                    r.update({k: body[k] for k in ("title", "notes", "url", "priority", "flagged", "tags", "assignee")})
                elif kind == "set-schedule":
                    r["schedule"] = body["schedule"]
                    if r["schedule"]:
                        r["schedule"]["occurrence"] = 0
                elif kind == "move-reminder":
                    r.update({k: body[k] for k in ("parent-id", "section-id", "rank")})
                elif kind == "set-completed":
                    if body.get("advance"):
                        r["schedule"].update(body["advance"])
                    else:
                        r["completed"] = body["completed"]
                elif kind == "delete-reminder":
                    target["reminders"].remove(r)
                else:
                    raise ValueError("unsupported fixture action")
                r["revision"] += 1
            if kind == "delete-list":
                result = {"list-deleted": {"operation-id": op, "list-id": target["id"]}}
            else:
                target["revision"] += 1
                result = {"list-upserted": {"operation-id": op, "list": target,
                                             "preferences": {"revision": 1, "default-list": 1}}}
        self.receipts[key] = copy.deepcopy(result)
        self.broadcast(ship, result)
        self.broadcast(ship, self.accesses(ship))


fixture = Fixture()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *_):
        pass

    def send(self, status, body=None, headers=None):
        payload = b"" if body is None else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(payload)

    def ship(self):
        cookie = self.headers.get("Cookie", "")
        for ship in fixture.states:
            if f"urbauth-{ship}=fixture-{ship[1:]}" in cookie:
                return ship
        self.send(403)
        return None

    def do_POST(self):
        if self.path == "/__reset":
            with fixture.lock:
                fixture.states = {ship: [initial(ship)] for ship in ("~zod", "~nec")}
                fixture.receipts.clear()
            return self.send(204)
        if self.path != "/~/login":
            return self.send(404)
        code = parse_qs(self.rfile.read(int(self.headers.get("Content-Length", "0"))).decode()).get("password", [""])[0]
        if code not in ("zod", "nec"):
            return self.send(403)
        self.send(204, headers={"Set-Cookie": f"urbauth-~{code}=fixture-{code}; HttpOnly; Path=/"})

    def do_PUT(self):
        ship = self.ship()
        if not ship:
            return
        commands = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        with fixture.lock:
            channel = fixture.channels.setdefault(self.path, dict(ship=ship, queue=queue.Queue(), subscribed=False))
            for command in commands:
                action = command["action"]
                if action == "subscribe":
                    channel["subscribed"] = True
                    fixture.event(channel, "subscribe", ok="ok")
                    fixture.event(channel, "diff", json=fixture.snapshot(ship))
                    fixture.event(channel, "diff", json=fixture.accesses(ship))
                elif action == "poke":
                    try:
                        fixture.mutate(ship, command["json"])
                        fixture.event(channel, "poke", ok="ok")
                    except (ValueError, KeyError, StopIteration, TypeError):
                        fixture.event(channel, "poke", err="invalid fixture action")
                elif action == "delete":
                    fixture.channels.pop(self.path, None)
                    channel["queue"].put(None)
        self.send(204)

    def do_GET(self):
        ship = self.ship()
        if not ship:
            return
        with fixture.lock:
            if self.path.startswith("/~/scry/tend/"):
                path = self.path.removeprefix("/~/scry/tend/").removesuffix(".json")
                if path == "whoami":
                    return self.send(200, ship)
                if path == "state":
                    return self.send(200, fixture.snapshot(ship))
                if path == "accesses":
                    return self.send(200, fixture.accesses(ship))
                if path == "invitations":
                    return self.send(200, {"invitations-updated": []})
                if path == "settings":
                    return self.send(200, {"local-settings-updated": {}})
                if path.startswith("receipt/"):
                    result = fixture.receipts.get((ship, path.split("/", 1)[1]))
                    return self.send(200 if result else 404, result)
                return self.send(404)
            channel = fixture.channels.get(self.path)
        if not channel:
            return self.send(404)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Connection", "close")
        self.end_headers()
        try:
            while True:
                try:
                    value = channel["queue"].get(timeout=5)
                except queue.Empty:
                    self.wfile.write(b": heartbeat\n\n")
                    self.wfile.flush()
                    continue
                if value is None:
                    break
                eid, message = value
                self.wfile.write(f"id: {eid}\ndata: {json.dumps(message)}\n\n".encode())
                self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=18787)
    args = parser.parse_args()
    print(f"Ephemeral Tend fixture on http://127.0.0.1:{args.port}; codes: zod / nec", flush=True)
    ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
