"""Check the optional roof peak in the production Graphstudio UI."""
import argparse
import json
import shutil
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://127.0.0.1:4186/')
parser.add_argument('--chromium', default=shutil.which('chromium') or shutil.which('chromium-browser'))
parser.add_argument('--artifacts', type=Path, default=Path('/tmp/roof-peak-browser'))
args = parser.parse_args()
args.artifacts.mkdir(parents=True, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=args.chromium, headless=True,
        args=['--no-sandbox', '--enable-unsafe-swiftshader'])
    page = browser.new_page(viewport={'width': 1600, 'height': 1100})
    page.set_default_timeout(20000)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(args.url.rstrip('/') + '/?editor=building')
    studio = page.locator('.studio:not([hidden])')
    studio.get_by_label('Beispiel öffnen', exact=True).select_option(label='Dachspitze bei ungerader Breite')
    roof = studio.locator('.react-flow__node[data-id="roof"]')
    expect(roof.get_by_text('Dachspitze (optional)', exact=True)).to_be_visible()
    roof.click()
    port = studio.get_by_label('Dachspitze (optional)', exact=True)
    expect(port).to_have_value('peak_copycat')
    assert 'Wall segment' not in port.locator('option').all_text_contents()
    studio.get_by_label('Vorschauziel', exact=True).select_option('selected')
    status = studio.locator('.preview-meta[role="status"]')
    expect(status).to_contain_text('Zinkeinheiten')
    expect(studio.locator('.preview-body canvas')).to_be_visible()
    def exported():
        with page.expect_download() as download:
            studio.get_by_role('button', name='JSON exportieren', exact=True).click()
        return json.loads(Path(download.value.path()).read_text())
    original = exported()
    def peak(document):
        return next(node for node in document['graph']['nodes'] if node['id'] == 'roof').get('roofPeak')
    assert peak(original) == 'peak_copycat'
    studio.get_by_role('button', name='3D einpassen', exact=True).click()
    page.wait_for_timeout(1000)
    page.screenshot(path=str(args.artifacts / 'connected.png'), full_page=True)
    port.select_option('')
    expect(port).to_have_value('')
    expect(status).to_contain_text('Zinkeinheiten')
    assert not peak(exported())
    page.wait_for_timeout(700)
    page.screenshot(path=str(args.artifacts / 'disconnected.png'), full_page=True)
    studio.get_by_role('button', name='Rückgängig', exact=True).click()
    expect(port).to_have_value('peak_copycat')
    assert exported() == original
    studio.get_by_role('button', name='Wiederholen', exact=True).click()
    expect(port).to_have_value('')
    assert not peak(exported())
    studio.get_by_role('button', name='Rückgängig', exact=True).click()
    expect(port).to_have_value('peak_copycat')
    expect(status).to_contain_text('Zinkeinheiten')
    assert not errors, errors
    print('PASS: example, visible typed port, 3D preview, disconnect, Undo/Redo and JSON export')
    browser.close()
