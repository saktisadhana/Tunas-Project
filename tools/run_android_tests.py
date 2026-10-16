"""Run instrumentation with an honest exit status and compact output.
Gradle assembles APKs first. Full raw instrumentation output is kept as evidence.
"""
from pathlib import Path
import os
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
adb = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
for apk in ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
    subprocess.run([str(adb), 'install', '-r', str(root / apk)], check=True)
command = [str(adb), 'shell', 'am', 'instrument', '-w', '-r']
if len(sys.argv) > 1:
    command += ['-e', 'class', sys.argv[1]]
if len(sys.argv) > 2:
    command += ['-e', 'persistencePhase', sys.argv[2]]
command += ['com.example.tunas.test/androidx.test.runner.AndroidJUnitRunner']
result = subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace')
evidence = root / 'design/verification'
evidence.mkdir(parents=True, exist_ok=True)
name = sys.argv[1].split('#')[-1].split('.')[-1] if len(sys.argv) > 1 else 'suite'
log = evidence / ('instrumentation-' + name + ('-' + sys.argv[2] if len(sys.argv) > 2 else '') + '.txt')
log.write_text(result.stdout + result.stderr, encoding='utf-8')
for line in result.stdout.splitlines():
    if any(token in line for token in ['test=', 'stack=', 'OK (', 'Tests run:', 'FAILURES', 'Time:', 'INSTRUMENTATION_FAILED']):
        print(line)
print('Full evidence:', log)
sys.exit(0 if result.returncode == 0 and re.search(r'OK \(\d+ tests?\)', result.stdout) else 1)
