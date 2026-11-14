"""Verify side HUD geometry and direct preview switching in the production UI."""
import argparse
from pathlib import Path
import shutil
from playwright.sync_api import sync_playwright, expect

parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://127.0.0.1:4186/')
parser.add_argument('--chromium', default=shutil.which('chromium') or shutil.which('chromium-browser'))
parser.add_argument('--artifacts', type=Path, default=Path('/tmp/preview-hud-browser'))
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
    panel = studio.get_by_role('region', name='Gebäude- und Formvorschau', exact=True)
    target = panel.get_by_role('group', name='Vorschauziel', exact=True)
    graph = target.get_by_role('button', name='Graph', exact=True)
    selected = target.get_by_role('button', name='Selektiert', exact=True)
    status = panel.locator('.preview-meta[role="status"]')
    expect(graph).to_have_attribute('aria-pressed', 'true')
    expect(panel.get_by_role('combobox', name='Vorschauziel')).to_have_count(0)
    expect(status).to_contain_text('Zinkeinheiten')
    selected.focus()
    page.keyboard.press('Enter')
    expect(selected).to_have_attribute('aria-pressed', 'true')
    expect(graph).to_have_attribute('aria-pressed', 'false')
    expect(status).to_contain_text('Wähle einen Knoten')
    studio.get_by_label('Beispiel öffnen', exact=True).select_option(label='Dachspitze bei ungerader Breite')
    studio.locator('.react-flow__node[data-id="roof"]').click()
    expect(status).to_contain_text('Zinkeinheiten')
    panel.get_by_role('button', name='3D einpassen', exact=True).click()

    def layout(label):
        # Scroll first, then let layout, ResizeObserver and the compositor settle.
        panel.scroll_into_view_if_needed()
        page.wait_for_timeout(500)
        left = panel.locator('.preview-hud-left').bounding_box()
        canvas = panel.locator('.viewer canvas').bounding_box()
        right = panel.locator('.preview-hud-right').bounding_box()
        body = panel.locator('.preview-body').bounding_box()
        outer = panel.bounding_box()
        inspector = studio.get_by_role('region', name='Knoteninspektor', exact=True).bounding_box()
        if inspector: assert outer['y'] + outer['height'] <= inspector['y'] + 1, (label, outer, inspector)
        assert left and canvas and right and body and outer
        assert body['y'] + body['height'] <= outer['y'] + outer['height'] + 1, (label, body, outer)
        assert left['x'] + left['width'] <= canvas['x'] + 1, (label, left, canvas)
        assert canvas['x'] + canvas['width'] <= right['x'] + 1, (label, canvas, right)
        assert abs(canvas['y'] - body['y']) <= 1, (label, canvas, body)
        assert abs(canvas['height'] - body['height']) <= 1, (label, canvas, body)
        assert canvas['width'] >= 90 and canvas['height'] >= 100, (label, canvas)
        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), label
        page.screenshot(path=str(args.artifacts / (label + '.png')))

    layout('desktop')
    # Reach the minimum sidebar width through the actual accessible splitter.
    splitter = studio.get_by_role('separator', name='Breite von Graph und Seitenleiste', exact=True)
    splitter.focus()
    page.keyboard.press('End')
    layout('narrow-sidebar')
    for direction in ['Oben', 'Unten', 'Nord', 'Ost', 'Süd', 'West']:
        button = panel.get_by_role('button', name='Ansicht ' + direction, exact=True)
        button.click()
        expect(button).to_have_attribute('aria-pressed', 'true')
    materials = panel.locator('.materials')
    materials.locator('summary').click()
    layout('materials-open')
    panel.get_by_role('button', name='3D-Vorschau einklappen', exact=True).click()
    expect(panel.locator('.preview-body')).to_be_hidden()
    panel.get_by_role('button', name='3D-Vorschau ausklappen', exact=True).click()
    layout('reopened')
    for width, height, label in [(820, 1180, 'tablet'), (390, 844, 'phone')]:
        page.set_viewport_size({'width': width, 'height': height})
        layout(label)
    # The shared standalone Viewer in the Copycat picker remains usable.
    studio.locator('.toolbar').get_by_role('button', name='Copycat auswählen', exact=True).click()
    dialog = page.get_by_role('dialog', name='Copycat auswählen', exact=True)
    dialog.get_by_role('searchbox').fill('Treppe')
    dialog.get_by_role('button', name='Treppe · Grundform', exact=True).click()
    expect(dialog.get_by_role('button', name='Auswahl übernehmen', exact=True)).to_be_enabled(timeout=30000)
    expect(dialog.locator('.copycat-preview-stage .viewer canvas')).to_be_visible()
    dialog.get_by_role('button', name='Ansicht Nord', exact=True).click()
    expect(dialog.get_by_role('button', name='Ansicht Nord', exact=True)).to_have_attribute('aria-pressed', 'true')
    page.screenshot(path=str(args.artifacts / 'picker-phone.png'))
    dialog.get_by_role('button', name='Abbrechen', exact=True).click()
    expect(selected).to_have_attribute('aria-pressed', 'true')
    graph.focus()
    page.keyboard.press('Space')
    expect(graph).to_have_attribute('aria-pressed', 'true')
    expect(status).to_contain_text('Zinkeinheiten')
    assert not errors, errors
    print('PASS: side HUD geometry, narrow sidebar, keyboard switch, side views, materials, collapse, tablet, phone and picker')
    browser.close()
