"""Tests for scripts/check_engine_releases.py (no network: the release lookups are passed in)."""

import shutil
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts"))

import check_engine_releases as cer  # noqa: E402


def next_version(version):
    """The pinned version with its last part raised by one: a release that is newer."""
    parts = version.split(".")
    return ".".join(parts[:-1] + [str(int(parts[-1]) + 1)])


def lookup(pypi, jdbc_versions=(), extension=True):
    metadata = "".join(f"<version>{v}</version>" for v in jdbc_versions)
    return {"pypi": lambda package: pypi[package], "maven": lambda: metadata,
            "exists": lambda url: extension}


@pytest.fixture
def repo(tmp_path):
    """A copy of the files the script reads and updates."""
    for pin_updates in cer.UPDATES.values():
        for files, _ in pin_updates:
            for name in files:
                (tmp_path / name).parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / name, tmp_path / name)
    return tmp_path


def test_every_pin_is_found_in_the_repository():
    pins = cer.current_pins(ROOT)
    assert set(pins) == {"duckdb", "duckdb_jdbc", "datafusion"}
    assert pins["duckdb_jdbc"].startswith(pins["duckdb"] + ".")


def test_no_update_when_the_pinned_version_is_the_latest():
    pins = cer.current_pins(ROOT)
    for engine in ("duckdb", "datafusion"):
        new, message = cer.check(engine, pins, lookup({"duckdb": pins["duckdb"], "datafusion": pins["datafusion"]}))
        assert new == {} and "is the latest release" in message


def test_an_older_or_pre_release_on_pypi_is_not_taken():
    pins = cer.current_pins(ROOT)
    for latest in ("0.1.0", next_version(pins["datafusion"]) + "rc1"):
        new, _ = cer.check("datafusion", pins, lookup({"datafusion": latest}))
        assert new == {}


def test_datafusion_update_is_applied_everywhere(repo):
    pins = cer.current_pins(repo)
    latest = next_version(pins["datafusion"])
    new, _ = cer.check("datafusion", pins, lookup({"datafusion": latest}))
    assert new == {"datafusion": latest}
    changed = cer.apply(repo, pins, new)
    assert cer.current_pins(repo)["datafusion"] == latest
    assert "README.md" in changed
    for name in changed:
        assert f'"{pins["datafusion"]}"' not in (repo / name).read_text()


def test_duckdb_waits_for_the_jdbc_driver():
    pins = cer.current_pins(ROOT)
    latest = next_version(pins["duckdb"])
    new, message = cer.check("duckdb", pins, lookup({"duckdb": latest}, jdbc_versions=[pins["duckdb_jdbc"]]))
    assert new == {} and "JDBC" in message


def test_duckdb_waits_for_the_substrait_extension():
    pins = cer.current_pins(ROOT)
    latest = next_version(pins["duckdb"])
    new, message = cer.check("duckdb", pins, lookup({"duckdb": latest}, [latest + ".0"], extension=False))
    assert new == {} and "substrait extension" in message


def test_duckdb_update_takes_the_newest_matching_jdbc_and_is_applied_everywhere(repo):
    pins = cer.current_pins(repo)
    latest = next_version(pins["duckdb"])
    jdbc = [latest + ".0", latest + ".1", latest + ".1.2", pins["duckdb_jdbc"]]
    new, _ = cer.check("duckdb", pins, lookup({"duckdb": latest}, jdbc))
    assert new == {"duckdb": latest, "duckdb_jdbc": latest + ".1"}
    changed = cer.apply(repo, pins, new)
    assert cer.current_pins(repo) == {**pins, **new}
    for name in changed:
        assert pins["duckdb_jdbc"] not in (repo / name).read_text()


def test_a_moved_pin_stops_the_update(repo):
    pins = cer.current_pins(repo)
    readme = repo / "README.md"
    readme.write_text(readme.read_text().replace(f"datafusion=={pins['datafusion']}", "datafusion"))
    with pytest.raises(SystemExit, match="README.md"):
        cer.apply(repo, pins, {"datafusion": next_version(pins["datafusion"])})


def test_outputs_for_the_workflow(repo, monkeypatch, tmp_path):
    pins = cer.current_pins(repo)
    latest = next_version(pins["datafusion"])
    monkeypatch.setattr(cer, "latest_pypi", lambda package: latest)
    out = tmp_path / "github_output"
    cer.main(["--engine", "datafusion", "--apply", "--output", str(out), "--root", str(repo)])
    outputs = dict(line.split("=", 1) for line in out.read_text().splitlines())
    assert outputs == {"version": latest, "branch": f"engine-update/datafusion-{latest}",
                       "title": f"chore(ci): run DataFusion {latest}", "workflows": "datafusion-compliance.yml"}
