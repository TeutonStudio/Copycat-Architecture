"""Exercise the rotation column through the production Graphstudio UI."""
import argparse
import json
import shutil
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://127.0.0.1:4186/')
parser.add_argument('--chromium', default=shutil.which('chromium') or shutil.which('chromium-browser'))
parser.add_argument('--artifacts', type=Path, default=Path('/tmp/rotation-segment-browser'))
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
    studio.get_by_label('Beispiel öffnen', exact=True).select_option(label='Rotationssegment · Säule')
    status = studio.locator('.preview-meta[role="status"]')
    expect(status).to_contain_text('Zinkeinheiten')
    column = studio.locator('.react-flow__node[data-id="column"]')
    column.click()
    length = studio.get_by_label('Länge (Y)', exact=True)
    expect(length).to_have_value('count')
    copycat = studio.get_by_label('Copycat 1', exact=True)
    expect(copycat).to_have_value('copycat')
    expect(studio.locator('[data-inspector-field="length"] output')).to_have_text('6')
    studio.get_by_role('group', name='Vorschauziel', exact=True).get_by_role('button', name='Selektiert', exact=True).click()
    expect(status).to_contain_text('Zinkeinheiten')
    def exported():
        with page.expect_download() as download:
            studio.get_by_role('button', name='JSON exportieren', exact=True).click()
        return json.loads(Path(download.value.path()).read_text())
    def node(document, id):
        return next(n for n in document['graph']['nodes'] if n['id'] == id)
    original = exported()
    assert node(original, 'column')['dimensions']['length'] == 'count'
    length.select_option('')
    expect(studio.locator('[data-inspector-field="length"] input')).to_have_value('4')
    expect(status).to_contain_text('Zinkeinheiten')
    assert not node(exported(), 'column').get('dimensions', {}).get('length')
    studio.get_by_role('button', name='Rückgängig', exact=True).click()
    expect(length).to_have_value('count')
    studio.get_by_role('button', name='Quelle bearbeiten', exact=True).click()
    value = studio.locator('[data-inspector-field="countValue"] input[type="number"]')
    expect(value).to_have_value('6')
    value.fill('9')
    studio.locator('.native-fields').get_by_role('button', name='Übernehmen', exact=True).click()
    assert node(exported(), 'count')['countValue'] == 9
    column.click()
    expect(studio.locator('[data-inspector-field="length"] output')).to_have_text('9')
    expect(status).to_contain_text('Zinkeinheiten')
    studio.get_by_role('button', name='3D einpassen', exact=True).click()
    page.wait_for_timeout(700)
    page.screenshot(path=str(args.artifacts / 'column.png'), full_page=True)
    studio.get_by_role('button', name='Rückgängig', exact=True).click()
    expect(studio.locator('[data-inspector-field="length"] output')).to_have_text('6')
    assert exported() == original
    assert not errors, errors
    print('PASS: typed inputs, live length, source editing, disconnect, Undo and JSON export')
    browser.close()
