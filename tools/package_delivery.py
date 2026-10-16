"""Package the built APK and actual emulator screenshots with verification metadata."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
dist.mkdir(exist_ok=True)
source = root / 'app/build/outputs/apk/debug/app-debug.apk'
apk = dist / 'Tunas-debug.apk'
shutil.copyfile(source, apk)
results = [ET.parse(p).getroot().attrib for p in (root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
unit = {key: sum(int(row.get(key, 0)) for row in results) for key in ('tests', 'failures', 'errors', 'skipped')}
lint = ET.parse(root / 'app/build/reports/lint-results-debug.xml').getroot()
issues = {level: sum(item.get('severity') == level for item in lint.findall('issue')) for level in ('Error', 'Fatal', 'Warning')}
run = (root / 'design/verification/instrumentation-suite.txt').read_text(encoding='utf-8')
match = re.search(r'OK \((\d+) tests?\)', run)
if not match or unit['failures'] or unit['errors'] or issues['Error'] or issues['Fatal']:
    raise SystemExit('Verification failed; refusing to package a passing report.')
metadata = {
    'apk': apk.name,
    'bytes': apk.stat().st_size,
    'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
    'unit_tests': unit,
    'instrumented_tests_passed': int(match.group(1)),
    'lint': issues,
    'physical_device_tested': False,
    'cloud_auth_connected': False,
    'iot_contract': 'Provisional BLE GATT; firmware alignment and hardware commissioning required',
}
(dist / 'verification.json').write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
font_path = Path('C:/Windows/Fonts/segoeui.ttf')
font = lambda size: ImageFont.truetype(str(font_path), size) if font_path.exists() else ImageFont.load_default()
width, gap, margin = 270, 16, 20
names = [('welcome', 'Welcome'), ('home', 'Home'), ('plants', 'Plants'), ('maintenance', 'Maintenance')]
images = []
for name, label in names:
    image = Image.open(root / 'design/verification/delivery' / (name + '.png')).convert('RGB')
    image.thumbnail((width, 800), Image.Resampling.LANCZOS)
    images.append((image, label))
height = max(image.height for image, _ in images)
sheet = Image.new('RGB', (margin * 2 + width * 4 + gap * 3, height + 132), '#EFEECF')
draw = ImageDraw.Draw(sheet)
draw.text((margin, 12), 'Tunas / Native Android', fill='#3B5142', font=font(24))
draw.text((margin, 44), 'Actual emulator renders · test profile and room · no sensor readings fabricated', fill='#454D39', font=font(15))
for index, (image, label) in enumerate(images):
    x = margin + index * (width + gap)
    draw.text((x, 76), label, fill='#3B5142', font=font(17))
    sheet.paste(image, (x, 104))
sheet.save(dist / 'Tunas-screens.png')
print(json.dumps(metadata, indent=2))
print('Screens:', dist / 'Tunas-screens.png')
