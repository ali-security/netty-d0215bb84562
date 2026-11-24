"""Print per-test skip/failure details and totals from Surefire XML reports.

Surefire 2.10 prints only per-class counts on the console; this lists every
skipped test with its reason and prints the overall totals as the last line.
"""
import glob
import sys
import xml.etree.ElementTree as ET


def ignore_origin(classname):
    """Locate the upstream @Ignore that disabled a test without a message."""
    path = "src/test/java/%s.java" % (classname or "").replace(".", "/")
    try:
        with open(path) as handle:
            lines = handle.readlines()
    except IOError:
        return "skipped without a message (source %s not found)" % path
    hits = [str(i + 1) for i, line in enumerate(lines) if "@Ignore" in line]
    return (
        "disabled by the netty project with a bare @Ignore at %s:%s "
        "(upstream gives no reason in source)" % (path, ",".join(hits) or "?")
    )


reports = sorted(glob.glob("target/surefire-reports/TEST-*.xml"))
total = failures = errors = skipped = 0
skipped_tests = []
failed_tests = []
for path in reports:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        print("unparseable report %s: %s" % (path, exc))
        continue
    for case in root.iter("testcase"):
        total += 1
        name = "%s.%s" % (case.get("classname"), case.get("name"))
        skip = case.find("skipped")
        if skip is not None:
            skipped += 1
            reason = skip.get("message") or (skip.text or "").strip()
            skipped_tests.append(
                (name, reason or ignore_origin(case.get("classname")))
            )
        if case.find("failure") is not None:
            failures += 1
            failed_tests.append(name)
        if case.find("error") is not None:
            errors += 1
            failed_tests.append(name)

print("==== Surefire per-test summary (%d report files) ====" % len(reports))
for name, reason in skipped_tests:
    print("SKIPPED: %s -- %s" % (name, reason))
for name in failed_tests:
    print("FAILED: %s" % name)
passed = total - failures - errors - skipped
print(
    "SUREFIRE TOTALS: tests=%d passed=%d failures=%d errors=%d skipped=%d"
    % (total, passed, failures, errors, skipped)
)
sys.exit(1 if (not reports or failures or errors) else 0)
